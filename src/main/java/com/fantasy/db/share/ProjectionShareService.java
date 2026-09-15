package com.fantasy.db.share;

import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.share.dto.SharedPlayer;
import com.fantasy.db.share.dto.SharedProjectionData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ProjectionShareService {

    private final ProjectionShareRepository projectionShareRepository;
    private final UserProjectionRepository userProjectionRepository;
    private final UserRepository userRepository;

    public ProjectionShareService(ProjectionShareRepository projectionShareRepository,
                                  UserProjectionRepository userProjectionRepository,
                                  UserRepository userRepository) {
        this.projectionShareRepository = projectionShareRepository;
        this.userProjectionRepository = userProjectionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Publishes a projection, once. The rows come from the caller, which owns the ranking; the
     * settings, name, season and position corrections are copied from the stored projection so a
     * client cannot publish a page that misrepresents the projection it points at.
     *
     * <p>A projection that is already shared keeps the share it has, untouched — there is no way
     * to refresh or withdraw a published snapshot. Deleting the projection deletes the share with
     * it (the row cascades), which is the only thing that takes a link down.
     */
    @Transactional
    public ProjectionShare share(UUID userId, UUID projectionId, List<SharedPlayer> players) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No user found with id: " + userId));
        if (owner.getUsername() == null || owner.getUsername().isBlank()) {
            // A public page has to credit someone, and the account is the only place that name
            // can come from now. Rejected here rather than trusted from the caller.
            throw new IllegalStateException("A username is required before sharing a projection");
        }
        UserProjection projection = userProjectionRepository.findByIdAndUserId(projectionId, userId)
                .orElseThrow(() -> new NoSuchElementException("No projection found with id: " + projectionId));
        SharedProjectionData data = new SharedProjectionData(
                publishable(projection.getData().settings()),
                players,
                projection.getData().positionOverrides());

        return projectionShareRepository.findByProjectionIdAndUserId(projectionId, userId)
                .orElseGet(() -> projectionShareRepository.save(ProjectionShare.create(
                        projectionId, userId, projection.getName(), projection.getSeason(), data,
                        projection.getPlayerIdSpace())));
    }

    @Transactional(readOnly = true)
    public ProjectionShare findByProjection(UUID userId, UUID projectionId) {
        return projectionShareRepository.findByProjectionIdAndUserId(projectionId, userId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No share found for projection with id: " + projectionId));
    }

    /**
     * The snapshot plus the owner's current name. The name is read now rather than copied at
     * share time, so renaming an account follows onto links already out there.
     */
    @Transactional(readOnly = true)
    public SharedProjection findByToken(String token) {
        ProjectionShare share = projectionShareRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("No share found for that token"));
        String authorUsername = userRepository.findById(share.getUserId())
                .map(User::getUsername)
                .orElseThrow(() -> new NoSuchElementException("No user found for that share"));
        return new SharedProjection(share, authorUsername);
    }


    /**
     * Strips the league sync details before the settings go on a public page — they carry the
     * owner's league name and its id on Yahoo or ESPN — including the remembered ESPN id, which
     * outlives the sync — and that is about their private league rather
     * than the projection anyone with the link came to look at. The player basis and pool stamp go
     * with them: a share is a frozen snapshot, so how its rows would be kept in step with the
     * player pool no longer says anything. So do the new players the owner has not acknowledged:
     * that notice is theirs, and an import would otherwise hand it to someone else.
     */
    private ProjectionSettings publishable(ProjectionSettings settings) {
        return new ProjectionSettings(
                settings.scoringType(),
                settings.statWeights(),
                settings.activeScoringColumns(),
                settings.activeUtilityColumns(),
                settings.scaleSettings(),
                settings.decimalSettings(),
                settings.useDefaultDecimals(),
                settings.leagueSize(),
                settings.rosterSlots(),
                settings.minGoalieGames(),
                null,
                null,
                null,
                null,
                null, null);
    }
}
