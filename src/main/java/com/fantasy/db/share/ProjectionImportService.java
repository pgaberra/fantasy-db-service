package com.fantasy.db.share;

import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.user.User;
import com.fantasy.db.share.dto.SharedPlayer;
import com.fantasy.db.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Copying a shared board into an account, so the holder of a link can draft against someone
 * else's numbers.
 *
 * <p>What is copied is the snapshot, not the projection behind it. The owner published a frozen
 * picture and consented to that much; every edit they have made since is theirs, and the copy
 * would be a different board than the one the link showed.
 */
@Service
public class ProjectionImportService {

    private final ProjectionShareRepository projectionShareRepository;
    private final UserProjectionRepository userProjectionRepository;
    private final UserRepository userRepository;

    public ProjectionImportService(ProjectionShareRepository projectionShareRepository,
                                   UserProjectionRepository userProjectionRepository,
                                   UserRepository userRepository) {
        this.projectionShareRepository = projectionShareRepository;
        this.userProjectionRepository = userProjectionRepository;
        this.userRepository = userRepository;
    }

    /**
     * The copy starts with no draft of its own: the picks on the shared projection were the
     * author's, and the point of importing is to draft against their numbers rather than to
     * inherit their draft. The season comes from the share for the same reason the rows do —
     * they are that season's numbers whatever season it is now.
     *
     * <p>The copy also starts with no position overrides. The author corrected those against the
     * platform their league runs on, which is not necessarily the importer's, and the importer's
     * own player pool already says which positions each player is eligible for.
     *
     * <p>Flushed rather than left to the commit so that a name the importer already used surfaces
     * here as a conflict, inside the boundary that maps it to a 409, rather than out of the
     * transaction after the handler has returned.
     */
    @Transactional
    public UserProjection importFrom(UUID userId, String token, String name) {
        ProjectionShare share = projectionShareRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("No share found for that token"));
        String authorUsername = userRepository.findById(share.getUserId())
                .map(User::getUsername)
                .orElseThrow(() -> new NoSuchElementException("No user found for that share"));
        ProjectionData data = new ProjectionData(
                share.getData().settings(), boardOf(share), null, null);

        return userProjectionRepository.saveAndFlush(UserProjection.importedFrom(
                userId,
                name == null || name.isBlank() ? share.getName() : name.trim(),
                share.getSeason(),
                data,
                share.getToken(),
                authorUsername,
                share.getPlayerIdSpace()));
    }

    /**
     * The published rows, stripped back to what a projection stores. A shared row carries the
     * identity and ranking the public page needs on top of that; the account importing it has a
     * player pool of its own to draw names from, and a ranking of its own to compute.
     */
    private static List<PlayerProjection> boardOf(ProjectionShare share) {
        return share.getData().players().stream()
                .map(ProjectionImportService::toPlayerProjection)
                .toList();
    }

    private static PlayerProjection toPlayerProjection(SharedPlayer player) {
        return new PlayerProjection(player.playerId(), player.type(), player.stats());
    }
}
