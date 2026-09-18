package com.fantasy.db.share;

import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserAvatar;
import com.fantasy.db.user.UserAvatarRepository;
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
    private final UserAvatarRepository userAvatarRepository;

    public ProjectionShareService(ProjectionShareRepository projectionShareRepository,
                                  UserProjectionRepository userProjectionRepository,
                                  UserRepository userRepository,
                                  UserAvatarRepository userAvatarRepository) {
        this.projectionShareRepository = projectionShareRepository;
        this.userProjectionRepository = userProjectionRepository;
        this.userRepository = userRepository;
        this.userAvatarRepository = userAvatarRepository;
    }

    /**
     * Publishes a projection, or publishes it again. The rows come from the caller, which owns the
     * ranking; the settings, name, season and position corrections are copied from the stored
     * projection so a client cannot publish a page that misrepresents the projection it points at.
     *
     * <p>A projection that is already shared keeps its token and has the copy behind it replaced,
     * so a link follows the projection rather than the moment it was first shared. The owner's
     * editor publishes again after every save of a shared projection. Deleting the projection
     * deletes the share with it (the row cascades), which is the only thing that takes a link
     * down.
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
                .map(existing -> {
                    existing.refresh(projection.getName(), data, projection.getPlayerIdSpace());
                    return projectionShareRepository.save(existing);
                })
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
     * The snapshot plus the owner's current name and the stamp on their picture. Both are read now
     * rather than copied at share time, so renaming an account or changing its picture follows
     * onto links already out there. Only the stamp travels with the snapshot; the picture itself
     * is fetched by {@link #findAuthorAvatar(String)}, so a page that draws it asks for the bytes
     * once and caches them under that stamp.
     */
    @Transactional(readOnly = true)
    public SharedProjection findByToken(String token) {
        ProjectionShare share = projectionShareRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("No share found for that token"));
        String authorUsername = userRepository.findById(share.getUserId())
                .map(User::getUsername)
                .orElseThrow(() -> new NoSuchElementException("No user found for that share"));
        return new SharedProjection(share, authorUsername,
                userAvatarRepository.findUpdatedAtByUserId(share.getUserId()).orElse(null));
    }

    /**
     * The picture of the account behind a link, looked up by the token alone: the caller serving
     * the public page never learns whose account it is.
     */
    @Transactional(readOnly = true)
    public UserAvatar findAuthorAvatar(String token) {
        ProjectionShare share = projectionShareRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("No share found for that token"));
        return userAvatarRepository.findById(share.getUserId())
                .orElseThrow(() -> new NoSuchElementException("No avatar for that share's author"));
    }


    /**
     * Strips the league sync details before the settings go on a public page — they carry the
     * owner's league name and its id on Yahoo or ESPN — including the remembered ESPN id, which
     * outlives the sync — and that is about their private league rather
     * than the projection anyone with the link came to look at. The player basis and pool stamp go
     * with them: they say how the owner's projection keeps its rows in step with the pool, which is
     * nothing a reader of the published rows can act on. So do the new players the owner has not acknowledged:
     * that notice is theirs, and an import would otherwise hand it to someone else.
     *
     * <p>A hand ranking is kept, for the reason the author's position corrections are: the board
     * on the page was ordered by it, and a copy that went back to the projected order would rank
     * differently from the page it was copied from.
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
                null,
                null,
                settings.manualRanking());
    }
}
