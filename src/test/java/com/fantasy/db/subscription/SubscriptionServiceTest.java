package com.fantasy.db.subscription;

import com.fantasy.db.subscription.dto.SubscriptionResponse;
import com.fantasy.db.subscription.dto.UpsertSubscriptionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    private static UpsertSubscriptionRequest eventFor(String subscriptionId, SubscriptionStatus status,
                                                      Instant eventAt) {
        return new UpsertSubscriptionRequest(
                "paddle", "ctm_1", subscriptionId, "price_1", status, Instant.now().plusSeconds(86_400), false,
                eventAt);
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

    /** Subscribing again after the old subscription ended is the ordinary case, and must still work. */
    @Test
    void aNewSubscriptionReplacesOneThatHasEnded() {
        subscriptionService.upsert(userId, eventFor("sub_1", SubscriptionStatus.CANCELED, EARLIER));

        Subscription result = subscriptionService.upsert(userId, eventFor("sub_2", SubscriptionStatus.ACTIVE, LATER));

        assertThat(result.getProviderSubscriptionId()).isEqualTo("sub_2");
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    /**
     * The bug this guards: a user with two subscriptions, one of which is canceled. The cancellation
     * is the newest event, and it used to overwrite the row, taking premium away from a user who was
     * still paying for the other subscription.
     */
    @Test
    void cancelingAnotherSubscriptionDoesNotEndTheLiveOne() {
        subscriptionService.upsert(userId, eventFor("sub_1", SubscriptionStatus.ACTIVE, EARLIER));

        Subscription result = subscriptionService.upsert(userId,
                eventFor("sub_2", SubscriptionStatus.CANCELED, LATER));

        assertThat(result.getProviderSubscriptionId()).isEqualTo("sub_1");
        assertThat(result.isPremium(Instant.now())).isTrue();
    }

    @Test
    void aSecondPaidSubscriptionDoesNotTakeOverFromAPaidOne() {
        subscriptionService.upsert(userId, eventFor("sub_1", SubscriptionStatus.ACTIVE, EARLIER));

        Subscription result = subscriptionService.upsert(userId, eventFor("sub_2", SubscriptionStatus.ACTIVE, LATER));

        assertThat(result.getProviderSubscriptionId()).isEqualTo("sub_1");
    }

    /** A paused subscription grants nothing, so a paid one arriving beside it is what the user has. */
    @Test
    void aPaidSubscriptionTakesOverFromAPausedOne() {
        subscriptionService.upsert(userId, eventFor("sub_1", SubscriptionStatus.PAUSED, EARLIER));

        Subscription result = subscriptionService.upsert(userId, eventFor("sub_2", SubscriptionStatus.ACTIVE, LATER));

        assertThat(result.getProviderSubscriptionId()).isEqualTo("sub_2");
        assertThat(result.isPremium(Instant.now())).isTrue();
    }

    @Test
    void laterEventsForTheStoredSubscriptionStillApply() {
        subscriptionService.upsert(userId, eventFor("sub_1", SubscriptionStatus.ACTIVE, EARLIER));

        Subscription result = subscriptionService.upsert(userId,
                eventFor("sub_1", SubscriptionStatus.CANCELED, LATER));

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"ACTIVE", "TRIALING", "PAST_DUE", "PAUSED", "UNPAID"})
    void aSubscriptionThatCanStillBillIsReportedLive(SubscriptionStatus status) {
        Subscription sub = subscriptionService.upsert(userId, eventFor("sub_1", status, EARLIER));

        assertThat(SubscriptionResponse.from(sub, Instant.now()).live()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"CANCELED", "INCOMPLETE"})
    void anEndedOrNeverStartedSubscriptionIsNotLive(SubscriptionStatus status) {
        Subscription sub = subscriptionService.upsert(userId, eventFor("sub_1", status, EARLIER));

        assertThat(SubscriptionResponse.from(sub, Instant.now()).live()).isFalse();
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

    /**
     * A paused subscription is a state the user can come back from, so it is stored as itself
     * rather than folded into canceled - but a paused period is not one anybody paid for, so it
     * grants nothing even while the old period end is still in the future.
     */
    @Test
    void notPremiumWhenPaused() {
        Subscription sub = subscriptionService.upsert(userId,
                request(SubscriptionStatus.PAUSED, Instant.now().plusSeconds(3600), false, EARLIER));

        assertThat(sub.isPremium(Instant.now())).isFalse();
    }
}
