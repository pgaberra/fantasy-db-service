package com.fantasy.db.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectionShareRepository extends JpaRepository<ProjectionShare, UUID> {

    Optional<ProjectionShare> findByToken(String token);

    Optional<ProjectionShare> findByProjectionIdAndUserId(UUID projectionId, UUID userId);
}
