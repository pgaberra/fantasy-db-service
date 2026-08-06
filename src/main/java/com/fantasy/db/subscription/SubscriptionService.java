package com.fantasy.db.subscription;

import com.fantasy.db.subscription.dto.UpsertSubscriptionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class SubscriptionService {

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
}
