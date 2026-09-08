package com.fantasy.db.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    @Query("select s from Subscription s where s.status in :statuses "
            + "and (s.currentPeriodEnd is null or s.currentPeriodEnd > :now)")
    List<Subscription> findPremium(@Param("statuses") Collection<String> statuses, @Param("now") Instant now);
}
