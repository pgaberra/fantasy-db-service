package com.fantasy.db.checkout;

import com.fantasy.db.checkout.dto.ReplacePendingCheckoutRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Holds each user's one open checkout, and replaces it only by compare-and-set.
 *
 * <p>The caller reads the checkout, decides it can no longer be paid, opens a new one with the
 * provider and stores it naming the one it read. If another request stored a checkout in between,
 * the store is refused (409), and the caller reads the winner and hands that one out instead. Two
 * tabs asking at the same moment therefore end up on the same checkout, which the provider lets
 * be paid only once.
 */
@Service
public class PendingCheckoutService {

    static final String CHANGED_SINCE_READ = "The pending checkout has changed since it was read";

    private final PendingCheckoutRepository repository;

    public PendingCheckoutService(PendingCheckoutRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PendingCheckout find(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No pending checkout for user: " + userId));
    }

    /**
     * Stores the user's checkout. With no {@code replacesReference} this must be the user's first,
     * and a second first one conflicts, either here or on the primary key when two arrive together.
     * With one, it replaces that checkout only if it is still the stored one.
     */
    @Transactional
    public PendingCheckout replace(UUID userId, ReplacePendingCheckoutRequest request) {
        Instant now = PendingCheckout.atColumnPrecision(Instant.now());
        if (request.replacesReference() == null) {
            if (repository.existsById(userId)) {
                throw new IllegalStateException(CHANGED_SINCE_READ);
            }
            return repository.saveAndFlush(new PendingCheckout(
                    userId, request.provider(), request.reference(), request.checkoutUrl(), now, now));
        }
        int replaced = repository.replaceIfCurrent(userId, request.provider(), request.reference(),
                request.checkoutUrl(), now, request.replacesReference());
        if (replaced == 0) {
            throw new IllegalStateException(CHANGED_SINCE_READ);
        }
        return find(userId);
    }
}
