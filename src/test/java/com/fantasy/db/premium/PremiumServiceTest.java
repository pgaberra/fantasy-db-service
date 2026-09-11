package com.fantasy.db.premium;

import com.fantasy.db.premium.dto.GrantPremiumRequest;
import com.fantasy.db.premium.dto.PremiumCustomerResponse;
import com.fantasy.db.premium.dto.PremiumEntitlementResponse;
import com.fantasy.db.subscription.Subscription;
import com.fantasy.db.subscription.SubscriptionRepository;
import com.fantasy.db.subscription.SubscriptionStatus;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PremiumService.class)
class PremiumServiceTest {

    @Autowired
    private PremiumService premiumService;

    @Autowired
    private PremiumGrantRepository premiumGrantRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    private User newUser(String email) {
        return userRepository.save(User.create(email, "hash"));
    }

    private static GrantPremiumRequest request(Instant expiresAt) {
        return new GrantPremiumRequest(expiresAt, "admin@example.com", "a friend");
    }

    private Subscription payingSubscription(UUID userId, Instant currentPeriodEnd) {
        Instant now = Instant.now();
        return subscriptionRepository.save(new Subscription(
                UUID.randomUUID(), userId, "paddle", "cus_1", "sub_1", "price_1",
                SubscriptionStatus.ACTIVE, currentPeriodEnd, false, now, now, now));
    }

    @Test
    void aGrantGivesPremiumUntilItExpires() {
        User user = newUser("friend@example.com");
        Instant expiry = Instant.now().plus(60, ChronoUnit.DAYS);

        premiumService.grant(user.getId(), request(expiry));

        PremiumEntitlementResponse entitlement = premiumService.entitlement(user.getId());
        assertThat(entitlement.premium()).isTrue();
        assertThat(entitlement.source()).isEqualTo(PremiumSource.GRANT);
        assertThat(entitlement.grantExpiresAt()).isCloseTo(expiry, within());
        assertThat(entitlement.premiumUntil()).isCloseTo(expiry, within());
        assertThat(entitlement.subscriptionStatus()).isNull();
    }

    @Test
    void anExpiredGrantGivesNothing() {
        User user = newUser("lapsed@example.com");
        Instant now = Instant.now();
        premiumGrantRepository.save(new PremiumGrant(UUID.randomUUID(), user.getId(), "admin@example.com",
                null, now.minus(60, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null, now));

        assertThat(premiumService.entitlement(user.getId()).premium()).isFalse();
        assertThat(premiumService.customers()).isEmpty();
    }

    @Test
    void aGrantIsActiveFromTheVeryInstantItStarts() {
        User user = newUser("instant@example.com");
        Instant startsAt = Instant.parse("2026-09-11T12:00:00.123456789Z");
        premiumGrantRepository.saveAndFlush(new PremiumGrant(UUID.randomUUID(), user.getId(), "admin@example.com",
                null, startsAt, startsAt.plus(60, ChronoUnit.DAYS), null, startsAt));

        assertThat(premiumGrantRepository.findActiveByUserId(user.getId(), startsAt)).hasSize(1);
        assertThat(premiumGrantRepository.findActive(startsAt)).hasSize(1);
    }

    @Test
    void aGrantIsNoLongerActiveAtTheInstantItExpires() {
        User user = newUser("expiring@example.com");
        Instant expiresAt = Instant.parse("2026-09-11T12:00:00.123456789Z");
        premiumGrantRepository.saveAndFlush(new PremiumGrant(UUID.randomUUID(), user.getId(), "admin@example.com",
                null, expiresAt.minus(60, ChronoUnit.DAYS), expiresAt, null, expiresAt.minus(60, ChronoUnit.DAYS)));

        assertThat(premiumGrantRepository.findActiveByUserId(user.getId(), expiresAt)).isEmpty();
        assertThat(premiumGrantRepository.findActive(expiresAt)).isEmpty();
    }

    @Test
    void revokingEndsThePremiumButKeepsTheRow() {
        User user = newUser("revoked@example.com");
        premiumService.grant(user.getId(), request(Instant.now().plus(60, ChronoUnit.DAYS)));

        premiumService.revokeActiveGrants(user.getId());

        assertThat(premiumService.entitlement(user.getId()).premium()).isFalse();
        assertThat(premiumService.grants(user.getId())).hasSize(1);
        assertThat(premiumService.grants(user.getId()).getFirst().getRevokedAt()).isNotNull();
    }

    @Test
    void revokingIsSafeWhenThereIsNothingToRevoke() {
        User user = newUser("nothing@example.com");

        premiumService.revokeActiveGrants(user.getId());

        assertThat(premiumService.entitlement(user.getId()).premium()).isFalse();
    }

    @Test
    void aGrantForAnUnknownUserIsRejected() {
        assertThatThrownBy(() -> premiumService.grant(UUID.randomUUID(),
                request(Instant.now().plus(30, ChronoUnit.DAYS))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void customersListsPayingSubscribersAndGrantedUsersApart() {
        User payer = newUser("payer@example.com");
        User friend = newUser("friend@example.com");
        payingSubscription(payer.getId(), Instant.now().plus(20, ChronoUnit.DAYS));
        premiumService.grant(friend.getId(), request(Instant.now().plus(60, ChronoUnit.DAYS)));

        List<PremiumCustomerResponse> customers = premiumService.customers();

        assertThat(customers).hasSize(2);
        PremiumCustomerResponse paying = byEmail(customers, "payer@example.com");
        assertThat(paying.source()).isEqualTo(PremiumSource.SUBSCRIPTION);
        assertThat(paying.provider()).isEqualTo("paddle");
        assertThat(paying.grantExpiresAt()).isNull();

        PremiumCustomerResponse granted = byEmail(customers, "friend@example.com");
        assertThat(granted.source()).isEqualTo(PremiumSource.GRANT);
        assertThat(granted.subscriptionStatus()).isNull();
        assertThat(granted.grantedBy()).isEqualTo("admin@example.com");
        assertThat(granted.grantReason()).isEqualTo("a friend");
    }

    @Test
    void aGrantOnTopOfASubscriptionRunsToWhicheverLasts() {
        User user = newUser("both@example.com");
        Instant grantExpiry = Instant.now().plus(90, ChronoUnit.DAYS);
        payingSubscription(user.getId(), Instant.now().plus(20, ChronoUnit.DAYS));
        premiumService.grant(user.getId(), request(grantExpiry));

        PremiumEntitlementResponse entitlement = premiumService.entitlement(user.getId());

        assertThat(entitlement.source()).isEqualTo(PremiumSource.BOTH);
        assertThat(entitlement.premiumUntil()).isCloseTo(grantExpiry, within());
        assertThat(premiumService.customers()).singleElement()
                .extracting(PremiumCustomerResponse::source).isEqualTo(PremiumSource.BOTH);
    }

    @Test
    void aCancelledSubscriptionIsNotACustomer() {
        User user = newUser("gone@example.com");
        Instant now = Instant.now();
        subscriptionRepository.save(new Subscription(UUID.randomUUID(), user.getId(), "paddle", "cus_2", "sub_2",
                "price_1", SubscriptionStatus.CANCELED, now.plus(20, ChronoUnit.DAYS), true, now, now, now));

        assertThat(premiumService.customers()).isEmpty();
        assertThat(premiumService.entitlement(user.getId()).premium()).isFalse();
        assertThat(premiumService.entitlement(user.getId()).subscriptionStatus())
                .isEqualTo(SubscriptionStatus.CANCELED);
    }

    private static PremiumCustomerResponse byEmail(List<PremiumCustomerResponse> customers, String email) {
        return customers.stream().filter(customer -> customer.email().equals(email)).findFirst().orElseThrow();
    }

    private static TemporalUnitOffset within() {
        return Assertions.within(1, ChronoUnit.SECONDS);
    }
}
