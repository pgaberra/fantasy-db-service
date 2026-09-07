package com.fantasy.db.share;

import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.projection.UserProjectionService;
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
    private final UserProjectionService userProjectionService;
    private final UserRepository userRepository;

    public ProjectionImportService(ProjectionShareRepository projectionShareRepository,
                                   UserProjectionRepository userProjectionRepository,
                                   UserProjectionService userProjectionService,
                                   UserRepository userRepository) {
        this.projectionShareRepository = projectionShareRepository;
        this.userProjectionRepository = userProjectionRepository;
        this.userProjectionService = userProjectionService;
        this.userRepository = userRepository;
    }

    /**
     * The copy starts with no draft of its own: the picks on the shared projection were the
     * author's, and the point of importing is to draft against their numbers rather than to
     * inherit their draft. The season comes from the share for the same reason the rows do —
     * they are that season's numbers whatever season it is now.
     *
     * <p>The author's position corrections do come with it. They are part of the board being
     * copied: the ranking was computed against those positions, and a copy that quietly moved
     * players back onto the read model's would rank differently from the page it was copied from.
     * The importer can undo any of them, or all of them at once, the same way the author made
     * them. A share published before shares carried the corrections has none, and the copy then
     * starts on the reported positions as it always did.
     *
     * <p>The name is held to the same rule a projection's own is, and against the same list: a
     * copy lands in the list beside the importer's own boards, so it may not arrive under a name
     * one of those already has. Where the caller named nothing, a taken name is <em>settled</em>
     * rather than refused — the copy becomes "Board (2)" — because importing the same link twice
     * is a thing people do, and answering it with a conflict left them to go and sort out the
     * naming themselves. A name the caller chose is still refused, since that one is theirs to
     * change. Still flushed rather than left to the commit, so that a name won
     * by a request racing this one surfaces here as a conflict, inside the boundary that maps it
     * to a 409, rather than out of the transaction after the handler has returned.
     */
    @Transactional
    public UserProjection importFrom(UUID userId, String token, String name) {
        ProjectionShare share = projectionShareRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("No share found for that token"));
        String authorUsername = userRepository.findById(share.getUserId())
                .map(User::getUsername)
                .orElseThrow(() -> new NoSuchElementException("No user found for that share"));
        ProjectionData data = new ProjectionData(
                share.getData().settings(), boardOf(share), null,
                share.getData().positionOverrides());
        String copyName = name == null || name.isBlank()
                // Nobody asked for this name, so a clash is not something to report back: the
                // importer pressed a button on a board, and a second copy of it is a reasonable
                // thing to want. A name they typed themselves is different, and still refused
                // below, because that one they can see and change.
                ? userProjectionService.freeNameFrom(userId, share.getName(), ProjectionKind.IMPORTED)
                : name.trim();
        userProjectionService.requireFreeName(userId, copyName, ProjectionKind.IMPORTED, null);

        return userProjectionRepository.saveAndFlush(UserProjection.importedFrom(
                userId,
                copyName,
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
