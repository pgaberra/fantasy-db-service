package com.fantasy.db.premium;

import com.fantasy.db.premium.dto.GrantPremiumRequest;
import com.fantasy.db.premium.dto.PremiumCustomerResponse;
import com.fantasy.db.premium.dto.PremiumEntitlementResponse;
import com.fantasy.db.subscription.Subscription;
import com.fantasy.db.subscription.SubscriptionRepository;
import com.fantasy.db.subscription.SubscriptionStatus;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PremiumService {

    private static final List<String> PREMIUM_STATUSES =
            List.of(SubscriptionStatus.ACTIVE.getCode(), SubscriptionStatus.TRIALING.getCode());

    private final PremiumGrantRepository premiumGrantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public PremiumService(PremiumGrantRepository premiumGrantRepository,
                          SubscriptionRepository subscriptionRepository,
                          UserRepository userRepository) {
        this.premiumGrantRepository = premiumGrantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PremiumEntitlementResponse entitlement(UUID userId) {
        Instant now = Instant.now();
        Optional<Subscription> subscription = subscriptionRepository.findByUserId(userId);
        Optional<PremiumGrant> grant = latestActiveGrant(userId, now);

        boolean subscriptionPremium = subscription.map(s -> s.isPremium(now)).orElse(false);
        Instant currentPeriodEnd = subscription.map(Subscription::getCurrentPeriodEnd).orElse(null);
        Instant grantExpiresAt = grant.map(PremiumGrant::getExpiresAt).orElse(null);

        if (!subscriptionPremium && grantExpiresAt == null && subscription.isEmpty()) {
            return PremiumEntitlementResponse.none();
        }
        return new PremiumEntitlementResponse(
                subscriptionPremium || grantExpiresAt != null,
                source(subscriptionPremium, grantExpiresAt != null),
                subscription.map(Subscription::getStatus).orElse(null),
                currentPeriodEnd,
                subscription.map(Subscription::isCancelAtPeriodEnd).orElse(false),
                grantExpiresAt,
                premiumUntil(subscriptionPremium, currentPeriodEnd, grantExpiresAt));
    }

    @Transactional(readOnly = true)
    public List<PremiumGrant> grants(UUID userId) {
        return premiumGrantRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public PremiumGrant grant(UUID userId, GrantPremiumRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("No user with id: " + userId);
        }
        return premiumGrantRepository.save(
                PremiumGrant.create(userId, request.grantedBy(), request.reason(), request.expiresAt()));
    }

    @Transactional
    public void revokeActiveGrants(UUID userId) {
        Instant now = Instant.now();
        List<PremiumGrant> active = premiumGrantRepository.findActiveByUserId(userId, now);
        active.forEach(grant -> grant.revoke(now));
        premiumGrantRepository.saveAll(active);
    }

    @Transactional(readOnly = true)
    public List<PremiumCustomerResponse> customers() {
        Instant now = Instant.now();
        Map<UUID, Subscription> subscriptions = subscriptionRepository.findPremium(PREMIUM_STATUSES, now).stream()
                .filter(subscription -> subscription.isPremium(now))
                .collect(Collectors.toMap(Subscription::getUserId, Function.identity(), (first, _) -> first));
        Map<UUID, PremiumGrant> grants = premiumGrantRepository.findActive(now).stream()
                .collect(Collectors.toMap(PremiumGrant::getUserId, Function.identity(),
                        (first, second) -> second.getExpiresAt().isAfter(first.getExpiresAt()) ? second : first));

        Set<UUID> userIds = new LinkedHashSet<>(subscriptions.keySet());
        userIds.addAll(grants.keySet());
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<PremiumCustomerResponse> customers = new ArrayList<>();
        for (UUID userId : userIds) {
            User user = users.get(userId);
            if (user == null) {
                continue;
            }
            customers.add(toCustomer(user, subscriptions.get(userId), grants.get(userId), now));
        }
        customers.sort(Comparator.comparing(PremiumCustomerResponse::userCreatedAt).reversed());
        return customers;
    }

    private PremiumCustomerResponse toCustomer(User user, Subscription subscription, PremiumGrant grant, Instant now) {
        boolean subscriptionPremium = subscription != null && subscription.isPremium(now);
        Instant currentPeriodEnd = subscription == null ? null : subscription.getCurrentPeriodEnd();
        Instant grantExpiresAt = grant == null ? null : grant.getExpiresAt();
        return new PremiumCustomerResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getUsername(),
                source(subscriptionPremium, grant != null),
                subscription == null ? null : subscription.getStatus(),
                subscription == null ? null : subscription.getProvider(),
                currentPeriodEnd,
                subscription != null && subscription.isCancelAtPeriodEnd(),
                grantExpiresAt,
                grant == null ? null : grant.getGrantedBy(),
                grant == null ? null : grant.getReason(),
                premiumUntil(subscriptionPremium, currentPeriodEnd, grantExpiresAt),
                user.getCreatedAt());
    }

    private Optional<PremiumGrant> latestActiveGrant(UUID userId, Instant now) {
        return premiumGrantRepository.findActiveByUserId(userId, now).stream()
                .max(Comparator.comparing(PremiumGrant::getExpiresAt));
    }

    private static PremiumSource source(boolean subscriptionPremium, boolean granted) {
        if (subscriptionPremium && granted) {
            return PremiumSource.BOTH;
        }
        if (subscriptionPremium) {
            return PremiumSource.SUBSCRIPTION;
        }
        return granted ? PremiumSource.GRANT : PremiumSource.NONE;
    }

    private static Instant premiumUntil(boolean subscriptionPremium, Instant currentPeriodEnd, Instant grantExpiresAt) {
        if (subscriptionPremium && currentPeriodEnd == null) {
            return null;
        }
        Instant subscriptionUntil = subscriptionPremium ? currentPeriodEnd : null;
        if (subscriptionUntil == null) {
            return grantExpiresAt;
        }
        if (grantExpiresAt == null) {
            return subscriptionUntil;
        }
        return subscriptionUntil.isAfter(grantExpiresAt) ? subscriptionUntil : grantExpiresAt;
    }
}
