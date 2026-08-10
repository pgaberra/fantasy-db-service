package com.fantasy.db.share;

import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
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

    public ProjectionShareService(ProjectionShareRepository projectionShareRepository,
                                  UserProjectionRepository userProjectionRepository) {
        this.projectionShareRepository = projectionShareRepository;
        this.userProjectionRepository = userProjectionRepository;
    }

    /**
     * Publishes (or re-publishes) a projection. The rows come from the caller, which owns the
     * ranking; the settings, name and season are copied from the stored projection so a client
     * cannot publish a page that misrepresents the projection it points at.
     */
    @Transactional
    public ProjectionShare share(UUID userId, UUID projectionId, String authorAlias, List<SharedPlayer> players) {
        UserProjection projection = userProjectionRepository.findByIdAndUserId(projectionId, userId)
                .orElseThrow(() -> new NoSuchElementException("No projection found with id: " + projectionId));
        SharedProjectionData data =
                new SharedProjectionData(publishable(projection.getData().settings()), players);

        return projectionShareRepository.findByProjectionIdAndUserId(projectionId, userId)
                .map(existing -> {
                    existing.refresh(authorAlias, projection.getName(), data);
                    return projectionShareRepository.save(existing);
                })
                .orElseGet(() -> projectionShareRepository.save(ProjectionShare.create(
                        projectionId, userId, authorAlias, projection.getName(), projection.getSeason(), data)));
    }

    @Transactional(readOnly = true)
    public ProjectionShare findByProjection(UUID userId, UUID projectionId) {
        return projectionShareRepository.findByProjectionIdAndUserId(projectionId, userId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No share found for projection with id: " + projectionId));
    }

    @Transactional
    public ProjectionShare findByToken(String token) {
        ProjectionShare share = projectionShareRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("No share found for that token"));
        projectionShareRepository.incrementViewCount(share.getId());
        return share;
    }

    @Transactional
    public void unshare(UUID userId, UUID projectionId) {
        projectionShareRepository.delete(findByProjection(userId, projectionId));
    }

    /**
     * Strips the Yahoo sync details before the settings go on a public page — they carry the
     * owner's league name and key, which is about their private league rather than the
     * projection anyone with the link came to look at.
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
                null);
    }
}
