package com.fantasy.db.subscription;

import com.fantasy.db.subscription.dto.UpsertSubscriptionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(SubscriptionService.class)
class SubscriptionServiceTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private final UUID userId = UUID.randomUUID();

    private static final Instant EARLIER = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-05T00:00:00Z");

    private static UpsertSubscriptionRequest request(SubscriptionStatus status, Instant currentPeriodEnd,
                                                     boolean cancelAtPeriodEnd, Instant eventAt) {
        return new UpsertSubscriptionRequest(
                "mock", "cus_1", "sub_1", "price_1", status, currentPeriodEnd, cancelAtPeriodEnd, eventAt);
    }

    @Test
    void createsAndFindsSubscription() {
        Subscription created = subscriptionService.upsert(userId,
                request(SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(3600), false, EARLIER));

        assertThat(created.getId()).isNotNull();
        Subscription found = subscriptionService.find(userId);
        assertThat(found.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(found.getProviderCustomerId()).isEqualTo("cus_1");
    }

    @Test
    void findThrowsWhenAbsent() {
        assertThatThrownBy(() -> subscriptionService.find(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void upsertUpdatesExistingSubscriptionInPlace() {
        subscriptionService.upsert(userId, request(SubscriptionStatus.ACTIVE, null, false, EARLIER));

        Subscription updated = subscriptionService.upsert(userId,
                request(SubscriptionStatus.CANCELED, null, true, LATER));

        assertThat(subscriptionRepository.findAll()).hasSize(1);
        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(updated.isCancelAtPeriodEnd()).isTrue();
    }

    @Test
    void ignoresAnEventOlderThanTheStoredOne() {
        subscriptionService.upsert(userId, request(SubscriptionStatus.ACTIVE, null, false, LATER));

        Subscription result = subscriptionService.upsert(userId,
                request(SubscriptionStatus.CANCELED, null, false, EARLIER));

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void enforcesOneSubscriptionPerUser() {
        subscriptionRepository.saveAndFlush(Subscription.create(
                userId, "mock", "cus_1", "sub_1", "price_1", SubscriptionStatus.ACTIVE, null, false, EARLIER));

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(Subscription.create(
                userId, "mock", "cus_2", "sub_2", "price_1", SubscriptionStatus.ACTIVE, null, false, LATER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void premiumWhenActiveAndPeriodInFuture() {
        Subscription sub = subscriptionService.upsert(userId,
                request(SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(3600), false, EARLIER));

        assertThat(sub.isPremium(Instant.now())).isTrue();
    }

    @Test
    void notPremiumWhenActiveButPeriodHasPassed() {
        Subscription sub = subscriptionService.upsert(userId,
                request(SubscriptionStatus.ACTIVE, Instant.now().minusSeconds(3600), false, EARLIER));

        assertThat(sub.isPremium(Instant.now())).isFalse();
    }

    @Test
    void notPremiumWhenCanceled() {
        Subscription sub = subscriptionService.upsert(userId,
                request(SubscriptionStatus.CANCELED, Instant.now().plusSeconds(3600), false, EARLIER));

        assertThat(sub.isPremium(Instant.now())).isFalse();
    }
}
