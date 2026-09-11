package com.fantasy.db.subscription;

import com.fantasy.db.subscription.dto.UpsertSubscriptionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(readOnly = true)
    public Subscription find(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("No subscription found for user: " + userId));
    }

    @Transactional
    public Subscription upsert(UUID userId, UpsertSubscriptionRequest request) {
        return subscriptionRepository.findByUserId(userId)
                .map(existing -> applyIfNewer(existing, request))
                .orElseGet(() -> subscriptionRepository.save(Subscription.create(
                        userId,
                        request.provider(),
                        request.providerCustomerId(),
                        request.providerSubscriptionId(),
                        request.priceId(),
                        request.status(),
                        request.currentPeriodEnd(),
                        request.cancelAtPeriodEnd(),
                        request.eventAt())));
    }

    private Subscription applyIfNewer(Subscription existing, UpsertSubscriptionRequest request) {
        if (existing.isNewerThan(request.eventAt())) {
            return existing;
        }
        if (isAnotherSubscription(existing, request) && !mayTakeOver(existing, request)) {
            return existing;
        }
        existing.updateFromProvider(
                request.provider(),
                request.providerCustomerId(),
                request.providerSubscriptionId(),
                request.priceId(),
                request.status(),
                request.currentPeriodEnd(),
                request.cancelAtPeriodEnd(),
                request.eventAt());
        return subscriptionRepository.save(existing);
    }

    private static boolean isAnotherSubscription(Subscription existing, UpsertSubscriptionRequest request) {
        return existing.getProviderSubscriptionId() != null
                && request.providerSubscriptionId() != null
                && !existing.getProviderSubscriptionId().equals(request.providerSubscriptionId());
    }

    /**
     * Whether an event about a different subscription may replace the one the user's row holds.
     *
     * <p>The row holds one subscription per user and used to follow whichever event came last, so
     * a user who ended up with two (two checkouts at once) lost premium the moment either one was
     * canceled, while the other was still being paid for.
     *
     * <p>An ended subscription gives way to a new one: that is an ordinary resubscription. A live
     * one never gives way to an event that is not itself live, and gives way to a live one only
     * when that one grants premium and the stored one does not, as a paid subscription does over
     * a paused one. Two live subscriptions at once mean a user may be paying twice, which is
     * logged at ERROR so that one of them gets refunded.
     */
    private boolean mayTakeOver(Subscription existing, UpsertSubscriptionRequest request) {
        if (!existing.getStatus().isLive()) {
            return true;
        }
        if (!request.status().isLive()) {
            return false;
        }
        // A literal on purpose: FindSecBugs flags any argument in a log call (CRLF_INJECTION_LOGS).
        // The two subscriptions are findable in the provider by the event that triggered this.
        log.error("A user has two live subscriptions at once; one of them should be refunded");
        Instant now = Instant.now();
        return Subscription.grantsPremium(request.status(), request.currentPeriodEnd(), now)
                && !existing.isPremium(now);
    }
}
