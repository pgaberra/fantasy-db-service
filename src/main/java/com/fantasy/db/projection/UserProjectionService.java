package com.fantasy.db.projection;

import com.fantasy.db.exception.ProjectionNameExistsException;
import com.fantasy.db.exception.ProjectionNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserProjectionService {

    private final UserProjectionRepository userProjectionRepository;

    public UserProjectionService(UserProjectionRepository userProjectionRepository) {
        this.userProjectionRepository = userProjectionRepository;
    }

    @Transactional(readOnly = true)
    public List<UserProjection> findAll(UUID userId) {
        return userProjectionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public UserProjection findById(UUID userId, UUID id) {
        return userProjectionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProjectionNotFoundException(id));
    }

    @Transactional
    public UserProjection create(UUID userId, String name, String data) {
        if (userProjectionRepository.existsByUserIdAndName(userId, name)) {
            throw new ProjectionNameExistsException(name);
        }
        try {
            return userProjectionRepository.save(UserProjection.create(userId, name, data));
        } catch (DataIntegrityViolationException e) {
            // Handles the race where two requests create the same name concurrently;
            // the unique constraint is the source of truth.
            throw new ProjectionNameExistsException(name);
        }
    }

    @Transactional
    public UserProjection update(UUID userId, UUID id, String name, String data) {
        UserProjection projection = findById(userId, id);
        projection.update(name, data);
        try {
            return userProjectionRepository.save(projection);
        } catch (DataIntegrityViolationException e) {
            throw new ProjectionNameExistsException(name);
        }
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        UserProjection projection = findById(userId, id);
        userProjectionRepository.delete(projection);
    }
}
