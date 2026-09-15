package com.fantasy.db.checkout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The checkout a user has open with the payment provider, at most one per user.
 *
 * <p>{@link Persistable} so that saving a new one is always an insert. The id is the user id, which
 * the caller assigns, and without this Spring Data would treat an entity with an id as existing and
 * merge it, quietly overwriting a checkout another request stored a moment earlier instead of
 * failing on the primary key.
 */
@Entity
@Table(name = "pending_checkouts")
public class PendingCheckout implements Persistable<UUID> {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 255)
    private String reference;

    @Column(name = "checkout_url", nullable = false, length = 2048)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected PendingCheckout() {
        // Required by JPA
    }

    public PendingCheckout(UUID userId, String provider, String reference, String checkoutUrl, Instant createdAt,
                           Instant updatedAt) {
        this.userId = userId;
        this.provider = provider;
        this.reference = reference;
        this.checkoutUrl = checkoutUrl;
        this.createdAt = atColumnPrecision(createdAt);
        this.updatedAt = atColumnPrecision(updatedAt);
    }

    /** TIMESTAMPTZ keeps microseconds, so an instant is held at that precision from the start. */
    static Instant atColumnPrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getReference() {
        return reference;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
