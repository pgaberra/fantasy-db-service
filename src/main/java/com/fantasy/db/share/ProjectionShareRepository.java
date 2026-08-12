package com.fantasy.db.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectionShareRepository extends JpaRepository<ProjectionShare, UUID> {

    Optional<ProjectionShare> findByToken(String token);

    Optional<ProjectionShare> findByProjectionIdAndUserId(UUID projectionId, UUID userId);

    // Counted with an atomic UPDATE rather than a read-modify-write on the entity, so concurrent
    // visitors on the same link cannot overwrite each other's increment.
    @Modifying
    @Query("update ProjectionShare share set share.viewCount = share.viewCount + 1 where share.id = :id")
    void incrementViewCount(@Param("id") UUID id);
}
