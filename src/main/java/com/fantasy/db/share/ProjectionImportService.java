package com.fantasy.db.share;

import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.projection.UserProjectionService;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Following a shared board, and taking a copy of one, so the holder of a link can draft against
 * someone else's numbers.
 *
 * <p>A follow is the author's board in the follower's account: it holds what the link holds, and
 * the author's next publish mirrors into it (see {@code ProjectionShareService}). A copy is the
 * reader's own board from the moment it is made, and nothing the author does afterwards reaches
 * it. What is followed or copied is the published board, not the projection behind it. The two
 * agree once the owner's editor has published its last save, but not while a save is still on its
 * way or when a publish failed, and the reader has to get the board the link showed.
 */
@Service
public class ProjectionImportService {

    /** What a copy of a shared board is offered as, before the numbering a taken name gets. */
    private static final String COPY_NAME_PREFIX = "Copy of ";

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
     * Follows a link, or hands back the follow of it the user already has.
     *
     * <p>Following is <b>idempotent</b>, and the database holds it to that: one follow per user
     * and token. A second press of the button on the same link is the same board, and two rows
     * mirroring one link would be two copies of one thing, both renamed under the author and
     * neither told apart in the list. A user who wants a board of their own takes a copy instead
     * ({@link #copy}).
     *
     * <p>The follow carries no draft of its own to begin with: the picks on the shared projection
     * were the author's, and the point of following is to draft against their numbers rather than
     * to inherit their draft. The season comes from the share for the same reason the rows do —
     * they are that season's numbers whatever season it is now. The author's position corrections
     * come too, since the ranking on the page was computed against them.
     *
     * <p>It is named after the share, and renamed with it, so the follower never names it and it
     * sits outside the names they hold (V25). Following your own link is refused: that board is
     * already in the account, and a follow of it would mirror a projection into itself.
     *
     * @param seenUpdatedAt the share's stamp as the reader last saw it, or null to follow the
     *     board as it is now. A link follows its projection, so the author can change the board
     *     while someone is reading it, and a reader who pressed the button on numbers they had
     *     read should be told rather than handed the newer ones unannounced.
     */
    @Transactional
    public Follow follow(UUID userId, String token, Instant seenUpdatedAt) {
        ProjectionShare share = readable(token, seenUpdatedAt);
        if (share.getUserId().equals(userId)) {
            throw new IllegalArgumentException(
                    "This link is your own board; it is already in your projections");
        }
        return userProjectionRepository.findByUserIdAndOriginShareToken(userId, share.getToken())
                .map(existing -> new Follow(existing, false))
                .orElseGet(() -> new Follow(create(userId, share), true));
    }

    /**
     * Takes the board out of a link and into the user's own projections, where it is theirs: their
     * name for it, their numbers to change, and untouched by whatever the author publishes next.
     *
     * <p>The user ends up following the link as well, unless it is their own. The copy is the
     * board at one moment, and the link is where the author's board goes on living; a reader who
     * copies it has said that is a board they want, and losing sight of it by copying it would be
     * the wrong way round.
     *
     * <p>The copy is offered as {@code "Copy of <the share's name>"}, numbered if the user already
     * holds that name, because that name is the server's suggestion and not worth refusing a copy
     * over. It starts with no draft, like a follow.
     */
    @Transactional
    public UserProjection copy(UUID userId, String token, Instant seenUpdatedAt) {
        ProjectionShare share = readable(token, seenUpdatedAt);
        if (!share.getUserId().equals(userId)
                && userProjectionRepository.findByUserIdAndOriginShareToken(userId, share.getToken())
                        .isEmpty()) {
            create(userId, share);
        }
        String name = userProjectionService.freeNameFrom(
                userId, COPY_NAME_PREFIX + share.getName(), ProjectionKind.PROJECTION);
        userProjectionService.requireFreeName(userId, name, ProjectionKind.PROJECTION, null);
        return userProjectionRepository.saveAndFlush(UserProjection.create(
                userId, name, ProjectionKind.PROJECTION, null, share.getSeason(),
                share.boardWith(null), share.getPlayerIdSpace()));
    }

    /**
     * The share behind a token, refused when it has moved on since the reader read it. Only the
     * latest board is kept, so refusing is the one honest answer; the page then reloads the board
     * and the reader can act on that.
     */
    private ProjectionShare readable(String token, Instant seenUpdatedAt) {
        ProjectionShare share = projectionShareRepository.findByToken(token)
                .orElseThrow(() -> new NoSuchElementException("No share found for that token"));
        if (seenUpdatedAt != null && share.getUpdatedAt().truncatedTo(ChronoUnit.MICROS)
                .isAfter(seenUpdatedAt.truncatedTo(ChronoUnit.MICROS))) {
            throw new ConcurrentModificationException(
                    "The board behind this link has changed since it was read");
        }
        return share;
    }

    /**
     * Flushed rather than left to the commit, so that a follow won by a request racing this one
     * surfaces here as a conflict, inside the boundary that maps it to a 409, rather than out of
     * the transaction after the handler has returned.
     */
    private UserProjection create(UUID userId, ProjectionShare share) {
        String authorUsername = userRepository.findById(share.getUserId())
                .map(User::getUsername)
                .orElseThrow(() -> new NoSuchElementException("No user found for that share"));
        return userProjectionRepository.saveAndFlush(UserProjection.followOf(
                userId,
                share.getName(),
                share.getSeason(),
                share.boardWith(null),
                share.getToken(),
                authorUsername,
                share.getPlayerIdSpace()));
    }

    /**
     * A follow, and whether this call is what made it — the difference between following a link
     * and having followed it already, which the caller answers as 201 or 200.
     */
    public record Follow(UserProjection projection, boolean created) {}
}
