package com.fantasy.db.premium;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PremiumGrantRepository extends JpaRepository<PremiumGrant, UUID> {

    List<PremiumGrant> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select g from PremiumGrant g where g.revokedAt is null and g.startsAt <= :now and g.expiresAt > :now")
    List<PremiumGrant> findActive(@Param("now") Instant now);

    @Query("select g from PremiumGrant g where g.userId = :userId and g.revokedAt is null "
            + "and g.startsAt <= :now and g.expiresAt > :now")
    List<PremiumGrant> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
