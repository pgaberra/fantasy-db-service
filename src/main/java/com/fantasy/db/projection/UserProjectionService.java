package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.PlayerProjection;
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
        // limited in number; a second one with a name already taken is rejected by the unique
        // index instead. The season is stamped from config, not supplied by the caller.
        if (alreadyHas(userId, kind, preset)) {
            throw new DataIntegrityViolationException(
                    "User already has a projection of kind " + kind.getCode()
                            + (preset == null ? "" : " for preset " + preset.getCode()));
        }
        return userProjectionRepository.save(
                UserProjection.create(userId, name, kind, preset, currentSeason, data, playerIdSpace));
    }

    private boolean alreadyHas(UUID userId, ProjectionKind kind, ProjectionPreset preset) {
        return kind.isUniquePerUser()
                && userProjectionRepository.existsByUserIdAndKindAndPreset(userId, kind, preset);
    }

    /**
     * Applies an update. The player rows are the one part a caller may omit — they are ~0.5 MB
     * and unchanged by most edits — in which case the stored ones are carried over. Merging here
     * rather than in the caller keeps the read and the write inside one transaction, so two
     * concurrent updates cannot interleave into a projection that is half old and half new.
     *
     * <p><b>An empty list counts as omitted.</b> A projection covers every player in the league,
     * so there is no such thing as one with no rows, and nothing can ask for that on purpose.
     * Meanwhile a caller easily sends one by accident: the BFF's OpenAPI-generated request model
     * initialises the field to an empty {@code ArrayList}, so a body that leaves {@code players}
     * out arrives here as empty rather than null. Treating that as "replace with nothing" wiped
     * every player from a projection the moment its owner changed a setting.
     */
    @Transactional
    public UserProjection update(UUID userId, UUID id, String name, UpdateProjectionData incoming) {
        UserProjection projection = findById(userId, id);
        List<PlayerProjection> incomingPlayers = incoming.players();
        List<PlayerProjection> players = incomingPlayers == null || incomingPlayers.isEmpty()
                ? projection.getData().players()
                : incomingPlayers;
        projection.update(name, new ProjectionData(incoming.settings(), players, incoming.draft(),
                incoming.positionOverrides()));
        return userProjectionRepository.save(projection);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        UserProjection projection = findById(userId, id);
        userProjectionRepository.delete(projection);
    }
}
