package com.fantasy.db.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProjectionRepository extends JpaRepository<UserProjection, UUID> {

    List<UserProjection> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserProjection> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndKind(UUID userId, ProjectionKind kind);
}
