package com.fantasy.db.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "ux_subscriptions_user_id", columnNames = "user_id"))
public class Subscription {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_customer_id", length = 255)
    private String providerCustomerId;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    @Column(name = "price_id", length = 255)
    private String priceId;

    // Stored as the status's string code (e.g. "active"), exposed as the SubscriptionStatus
    // enum. Kept as a plain String column so Hibernate doesn't auto-generate an enum CHECK
    // constraint on the constant names (which wouldn't match the stored codes) — same rationale
    // as UserProjection.season.
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // Required by JPA
    }

    public Subscription(UUID id, UUID userId, String provider, String providerCustomerId,
                        String providerSubscriptionId, String priceId, SubscriptionStatus status,
                        Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant lastEventAt,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerCustomerId = providerCustomerId;
        this.providerSubscriptionId = providerSubscriptionId;
        this.priceId = priceId;
        this.status = status.getCode();
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.lastEventAt = lastEventAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Subscription create(UUID userId, String provider, String providerCustomerId,
                                      String providerSubscriptionId, String priceId, SubscriptionStatus status,
                                      Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant eventAt) {
        Instant now = Instant.now();
        return new Subscription(UUID.randomUUID(), userId, provider, providerCustomerId, providerSubscriptionId,
                priceId, status, currentPeriodEnd, cancelAtPeriodEnd, eventAt, now, now);
    }

    public void updateFromProvider(String provider, String providerCustomerId, String providerSubscriptionId,
                                   String priceId, SubscriptionStatus status, Instant currentPeriodEnd,
                                   boolean cancelAtPeriodEnd, Instant eventAt) {
        this.provider = provider;
        this.providerCustomerId = providerCustomerId;
        this.providerSubscriptionId = providerSubscriptionId;
        this.priceId = priceId;
        this.status = status.getCode();
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.lastEventAt = eventAt;
        this.updatedAt = Instant.now();
    }

    public boolean isPremium(Instant now) {
        SubscriptionStatus current = getStatus();
        boolean activeState = current == SubscriptionStatus.ACTIVE || current == SubscriptionStatus.TRIALING;
        return activeState && (currentPeriodEnd == null || currentPeriodEnd.isAfter(now));
    }

    public boolean isNewerThan(Instant eventAt) {
        return lastEventAt != null && lastEventAt.isAfter(eventAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderCustomerId() {
        return providerCustomerId;
    }

    public String getProviderSubscriptionId() {
        return providerSubscriptionId;
    }

    public String getPriceId() {
        return priceId;
    }

    public SubscriptionStatus getStatus() {
        return SubscriptionStatus.fromCode(status);
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
