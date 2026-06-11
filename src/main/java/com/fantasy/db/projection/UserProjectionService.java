package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.ProjectionData;
import org.springframework.beans.factory.annotation.Value;
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
    public UserProjection create(UUID userId, String name, ProjectionData data) {
        // The season is stamped from config, not supplied by the caller. The unique
        // (user_id, name) constraint is the source of truth for duplicates; a violation
        // surfaces as DataIntegrityViolationException -> 409 (see GlobalExceptionHandler).
        return userProjectionRepository.save(UserProjection.create(userId, name, currentSeason, data));
    }

    @Transactional
    public UserProjection update(UUID userId, UUID id, String name, ProjectionData data) {
        UserProjection projection = findById(userId, id);
        projection.update(name, data);
        return userProjectionRepository.save(projection);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        UserProjection projection = findById(userId, id);
        userProjectionRepository.delete(projection);
    }
}
