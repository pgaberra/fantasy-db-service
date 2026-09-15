package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PositionOverride;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.UpdateProjectionData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        // Each user may keep at most one draft per preset — the presets are separate starting
        // points, and drafting against one is no reason to be barred from the other. Reject a
        // second with a conflict (DataIntegrityViolationException -> 409, see
        // GlobalExceptionHandler). Projections a user makes and boards they import are not
        // limited in number, only in what they may be called. The season is stamped from config,
        // not supplied by the caller.
        if (alreadyHas(userId, kind, preset)) {
            throw new DataIntegrityViolationException(
                    "User already has a projection of kind " + kind.getCode()
                            + (preset == null ? "" : " for preset " + preset.getCode()));
        }
        requireFreeName(userId, name, kind, null);
        return userProjectionRepository.save(
                UserProjection.create(userId, name, kind, preset, currentSeason, data, playerIdSpace));
    }

    private boolean alreadyHas(UUID userId, ProjectionKind kind, ProjectionPreset preset) {
        return kind.isUniquePerUser()
                && userProjectionRepository.existsByUserIdAndKindAndPreset(userId, kind, preset);
    }

    /**
     * Refuses a name the user is already keeping something else under. One namespace covers
     * everything they can see and name — their own projections and the boards they imported —
     * because those are listed together and read by name, so two rows sharing one are only told
     * apart by the smaller line beneath them.
     *
     * <p>A preset draft is outside it, in both directions: the server names one after its preset
     * and never lists it as the user's own work, so it neither takes a name from the user nor may
     * be blocked by one they have taken.
     *
     * <p>The partial unique index added in V19 says the same thing and is what makes it true under
     * a race. This check is what makes the failure legible: it names the projection that is in the
     * way, before a row is written.
     *
     * <p>Public because there are two write paths into this table and one rule over both: a
     * projection created here, and a board imported by {@code ProjectionImportService}. A copy of
     * this check living beside the other write is a copy that can drift.
     *
     * @param excludedId the row being renamed, which is not its own conflict; null when creating
     */
    public void requireFreeName(UUID userId, String name, ProjectionKind kind, UUID excludedId) {
        if (kind == ProjectionKind.PRESET_DRAFT) {
            return;
        }
        boolean taken = excludedId == null
                ? userProjectionRepository.existsByUserIdAndNameAndKindNot(
                        userId, name, ProjectionKind.PRESET_DRAFT)
                : userProjectionRepository.existsByUserIdAndNameAndKindNotAndIdNot(
                        userId, name, ProjectionKind.PRESET_DRAFT, excludedId);
        if (taken) {
            throw new DataIntegrityViolationException(
                    "User already has a projection named " + name);
        }
    }

    /**
     * The name a copy can actually be saved under: the preferred one where it is free, and
     * {@code "<preferred> (2)"}, {@code " (3)"} and so on where it is not.
     *
     * <p>The same shape {@code V19} used when it had to break the ties already in the table, so a
     * board renamed by that migration and one imported today read alike. Truncated the same way
     * too — the suffix has to fit inside the hundred characters a name gets, and a name at the
     * cap would otherwise grow past it.
     *
     * <p>Bounded, and the bound is not a formality: the unique index is what actually settles a
     * race, so two imports landing together can still collide on a name this found free a moment
     * ago. Running out returns the preferred name and lets {@link #requireFreeName} refuse it,
     * which is the answer the caller used to get for every repeat import.
     */
    public String freeNameFrom(UUID userId, String preferred, ProjectionKind kind) {
        if (kind == ProjectionKind.PRESET_DRAFT || !isTaken(userId, preferred)) {
            return preferred;
        }
        for (int suffix = 2; suffix <= MAX_NAME_ATTEMPTS; suffix++) {
            String candidate = withSuffix(preferred, suffix);
            if (!isTaken(userId, candidate)) {
                return candidate;
            }
        }
        return preferred;
    }

    private boolean isTaken(UUID userId, String name) {
        return userProjectionRepository.existsByUserIdAndNameAndKindNot(
                userId, name, ProjectionKind.PRESET_DRAFT);
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

    @Transactional
    public void delete(UUID userId, UUID id) {
        UserProjection projection = findById(userId, id);
        userProjectionRepository.delete(projection);
    }
}
