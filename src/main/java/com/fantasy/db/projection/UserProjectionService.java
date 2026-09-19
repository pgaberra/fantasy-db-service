package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PositionOverride;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.UpdateProjectionData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserProjectionService {

    /** What the name column and every request DTO cap a name at. */
    private static final int MAX_NAME_LENGTH = 100;

    /** How many numbered names to try before giving up and letting the clash be reported. */
    private static final int MAX_NAME_ATTEMPTS = 100;

    private final UserProjectionRepository userProjectionRepository;
    private final Season currentSeason;

    public UserProjectionService(
            UserProjectionRepository userProjectionRepository,
            @Value("${projections.current-season}") String currentSeasonCode) {
        this.userProjectionRepository = userProjectionRepository;
        // Fail fast at startup if the configured season isn't a known one.
        this.currentSeason = Season.fromCode(currentSeasonCode);
    }

    @Transactional(readOnly = true)
    public List<UserProjection> findAll(UUID userId) {
        return userProjectionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public UserProjection findById(UUID userId, UUID id) {
        return userProjectionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("No projection found with id: " + id));
    }

    @Transactional
    public UserProjection create(UUID userId, String name, ProjectionKind kind,
                                 ProjectionPreset preset, ProjectionData data,
                                 PlayerIdSpace playerIdSpace) {
        // Nothing stored here is limited in number any more. A user's own boards and the ones
        // they import never were; drafts stopped being, once a draft became a row of its own
        // rather than a field on the board it was started from - ten mocks off one projection is
        // a normal thing to want, and the old model could not hold the second one. A name the
        // user already holds is numbered rather than refused. The season is stamped from config,
        // not supplied by the caller.
        String savedName = freeNameFrom(userId, name, kind);
        requireFreeName(userId, savedName, kind, null);
        return userProjectionRepository.save(
                UserProjection.create(userId, savedName, kind, preset, currentSeason, data, playerIdSpace));
    }

    /**
     * Starts a draft against one of the user's own boards: a copy of that board's rows and
     * position corrections, with the draft's own setup in it and a name of its own.
     *
     * <p>A copy, not a reference. A draft ranks by the numbers it was started against, and those
     * have to stop moving once it has: an edit in the editor mid-draft would re-rank a board
     * somebody is picking from, and the board's owner may delete it while a draft against it is
     * still open. It is also what makes a second draft off the same board free of the first.
     *
     * <p>The name defaults to the board's, numbered where another draft holds it already
     * ("Board (2)"): starting a draft is a button press and not a form, so a clash is settled
     * here rather than reported back. What the row was saved under is in the response.
     */
    @Transactional
    public UserProjection startDraft(UUID userId, UUID sourceId, String name, ProjectionData setup) {
        UserProjection source = findById(userId, sourceId);
        if (source.getKind().isDraft()) {
            throw new IllegalArgumentException(
                    "A draft is drafted against a board, not against another draft");
        }
        ProjectionData data = new ProjectionData(
                setup.settings(), source.getData().players(), setup.draft(),
                source.getData().positionOverrides());
        String preferred = name == null || name.isBlank() ? source.getName() : name.trim();
        String savedName = freeNameFrom(userId, preferred, ProjectionKind.DRAFT);
        requireFreeName(userId, savedName, ProjectionKind.DRAFT, null);
        return userProjectionRepository.save(UserProjection.draftFrom(source, savedName, data));
    }

    /**
     * Renames a row. Unlike a create, a taken name is refused rather than numbered: the name is
     * the whole of what was asked for, and the page that asked can say so.
     *
     * @param autoNamed false when the name is one its owner typed, which then stands against
     *     anything that would otherwise derive a name for it - see {@link #renameDerived}.
     */
    @Transactional
    public UserProjection rename(UUID userId, UUID id, String name, boolean autoNamed) {
        UserProjection projection = findById(userId, id);
        String trimmed = name.trim();
        requireFreeName(userId, trimmed, projection.getKind(), id);
        projection.rename(trimmed, autoNamed);
        return userProjectionRepository.save(projection);
    }

    /**
     * Renames a row to a name the server derived - a draft taking the name of the league it was
     * just synced with - and does nothing at all where the owner has named it themselves. A
     * clash is numbered rather than refused, since nobody typed this name either.
     *
     * <p>Returns the row as it stands, renamed or not, so the caller shows what is stored rather
     * than what it proposed.
     */
    @Transactional
    public UserProjection renameDerived(UUID userId, UUID id, String name) {
        UserProjection projection = findById(userId, id);
        if (!projection.isAutoNamed()) {
            return projection;
        }
        String preferred = name.trim();
        if (preferred.equals(projection.getName())) {
            return projection;
        }
        String savedName = freeNameFrom(userId, preferred, projection.getKind(), id);
        requireFreeName(userId, savedName, projection.getKind(), id);
        projection.rename(savedName, true);
        return userProjectionRepository.save(projection);
    }

    /**
     * Refuses a name the user is already keeping something else under, within the namespace that
     * name lives in. One namespace covers the boards they can see and name - their own
     * projections and the ones they imported - because those are listed together and read by
     * name, so two rows sharing one are only told apart by the smaller line beneath them.
     *
     * <p>Their drafts are a second namespace, apart from the first in both directions. A draft is
     * named after the board it was started from, so a shared namespace would number every draft
     * the moment it was created; and the two are never listed together.
     *
     * <p>The partial unique indexes added in V19 and V25 say the same thing and are what make it
     * true under a race. This check is what makes the failure legible: it names the projection
     * that is in the way, before a row is written.
     *
     * <p>This is the refusal a <b>rename</b> gets, and the last word after a create or an import
     * has already settled on a name with {@link #freeNameFrom}: there the name is free by the
     * time it is checked, so only a request racing this one can trip it.
     *
     * <p>Public because there are three write paths into this table and one rule over all of
     * them: a projection created here, a draft started here, and a board imported by
     * {@code ProjectionImportService}. A copy of this check living beside another write is a copy
     * that can drift.
     *
     * @param excludedId the row being renamed, which is not its own conflict; null when creating
     */
    public void requireFreeName(UUID userId, String name, ProjectionKind kind, UUID excludedId) {
        if (isTaken(userId, name, kind, excludedId)) {
            throw new DataIntegrityViolationException(
                    "User already has a projection named " + name);
        }
    }

    /** The kinds a name has to be distinct across, for a row of this kind. */
    private static Set<ProjectionKind> namespaceOf(ProjectionKind kind) {
        return kind.isDraft()
                ? EnumSet.of(ProjectionKind.DRAFT)
                : EnumSet.complementOf(EnumSet.of(ProjectionKind.DRAFT));
    }

    /**
     * The name a copy can actually be saved under: the preferred one where it is free, and
     * {@code "<preferred> (2)"}, {@code " (3)"} and so on where it is not.
     *
     * <p>The same shape {@code V19} used when it had to break the ties already in the table, so a
     * board renamed by that migration, one imported today and a second draft off one projection
     * all read alike. Truncated the same way too - the suffix has to fit inside the hundred
     * characters a name gets, and a name at the cap would otherwise grow past it.
     *
     * <p>Bounded, and the bound is not a formality: the unique index is what actually settles a
     * race, so two writes landing together can still collide on a name this found free a moment
     * ago. Running out returns the preferred name and lets {@link #requireFreeName} refuse it,
     * which is the answer the caller used to get for every repeat.
     *
     * <p>Every create goes through here, not only an import. Saving a board is not a question the
     * server should answer with "no": the numbers a user came for are already made, and a name
     * they can rename afterwards is a smaller thing than losing the save. A <b>rename</b> is
     * still refused, because there the name is the whole of what was asked for.
     */
    public String freeNameFrom(UUID userId, String preferred, ProjectionKind kind) {
        return freeNameFrom(userId, preferred, kind, null);
    }

    /** @param excludedId the row being renamed, which is not its own conflict; null on a create */
    public String freeNameFrom(UUID userId, String preferred, ProjectionKind kind, UUID excludedId) {
        if (!isTaken(userId, preferred, kind, excludedId)) {
            return preferred;
        }
        for (int suffix = 2; suffix <= MAX_NAME_ATTEMPTS; suffix++) {
            String candidate = withSuffix(preferred, suffix);
            if (!isTaken(userId, candidate, kind, excludedId)) {
                return candidate;
            }
        }
        return preferred;
    }

    private boolean isTaken(UUID userId, String name, ProjectionKind kind, UUID excludedId) {
        return excludedId == null
                ? userProjectionRepository.existsByUserIdAndNameAndKindIn(
                        userId, name, namespaceOf(kind))
                : userProjectionRepository.existsByUserIdAndNameAndKindInAndIdNot(
                        userId, name, namespaceOf(kind), excludedId);
    }

    private static String withSuffix(String preferred, int suffix) {
        String tail = " (" + suffix + ")";
        int room = MAX_NAME_LENGTH - tail.length();
        return (preferred.length() > room ? preferred.substring(0, room) : preferred) + tail;
    }

    /**
     * Applies an update. The player rows are the one part a caller may omit — they are ~0.5 MB
     * and unchanged by most edits — in which case the stored ones are carried over. Merging here
     * rather than in the caller keeps the read and the write inside one transaction, so two
     * concurrent updates cannot interleave into a projection that is half old and half new.
     *
     * <p>The overrides are kept the same way, and for a different reason: draft mode and the
     * clear-draft path both send an update built from the settings alone, and neither has any
     * business clearing the positions their owner corrected. An <b>empty</b> list is the one
     * thing that does clear them, since that is the app resetting every player back to the read
     * model.
     *
     * <p><b>An empty player list counts as omitted.</b> A projection covers every player in the league,
     * so there is no such thing as one with no rows, and nothing can ask for that on purpose.
     * Meanwhile a caller easily sends one by accident: the BFF's OpenAPI-generated request model
     * initialises the field to an empty {@code ArrayList}, so a body that leaves {@code players}
     * out arrives here as empty rather than null. Treating that as "replace with nothing" wiped
     * every player from a projection the moment its owner changed a setting.
     */
    @Transactional
    public UserProjection update(UUID userId, UUID id, String name, UpdateProjectionData incoming) {
        return update(userId, id, name, incoming, null);
    }

    /**
     * @param callerSpace the numbering the incoming rows are keyed by, or null when the caller
     *     does not say. A caller serving another platform's pool than the one the stored rows are
     *     keyed by is refused before anything is written: its rows are the pool it can draw, so a
     *     save would drop every stored row that pool does not carry and put some under the wrong
     *     player, which is what switching the pool without migrating the rows first does.
     */
    @Transactional
    public UserProjection update(UUID userId, UUID id, String name, UpdateProjectionData incoming,
                                 PlayerIdSpace callerSpace) {
        UserProjection projection = findById(userId, id);
        if (callerSpace != null && callerSpace != projection.getPlayerIdSpace()) {
            throw new IllegalStateException(("This projection's player ids are %s's and the "
                    + "update was built from %s's player pool. Nothing was written.")
                    .formatted(projection.getPlayerIdSpace().getCode(), callerSpace.getCode()));
        }
        // A rename has to answer to the same rule a new name does, or the rule is only a rule
        // until someone edits. Its own row is not the conflict.
        requireFreeName(userId, name, projection.getKind(), id);
        List<PlayerProjection> incomingPlayers = incoming.players();
        List<PlayerProjection> players = incomingPlayers == null || incomingPlayers.isEmpty()
                ? projection.getData().players()
                : incomingPlayers;
        List<PositionOverride> overrides = incoming.positionOverrides() == null
                ? projection.getData().positionOverrides()
                : incoming.positionOverrides();
        projection.update(name, new ProjectionData(incoming.settings(), players, incoming.draft(),
                overrides));
        return userProjectionRepository.save(projection);
    }

    /**
     * Deletes a row. A draft started from it keeps everything it holds - the numbers are its own
     * copy - and only forgets where they came from. Unhooked here rather than by a foreign key,
     * so that what the tests run against and what production runs are the same thing.
     */
    @Transactional
    public void delete(UUID userId, UUID id) {
        UserProjection projection = findById(userId, id);
        List<UserProjection> drafts = userProjectionRepository.findBySourceProjectionId(id);
        drafts.forEach(UserProjection::clearSourceProjection);
        userProjectionRepository.saveAll(drafts);
        userProjectionRepository.delete(projection);
    }
}
