package com.fantasy.db.premium;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "premium_grants")
public class PremiumGrant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "granted_by", nullable = false, length = 255, updatable = false)
    private String grantedBy;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PremiumGrant() {
        // Required by JPA
    }

    public PremiumGrant(UUID id, UUID userId, String grantedBy, String reason, Instant startsAt,
                        Instant expiresAt, Instant revokedAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.grantedBy = grantedBy;
        this.reason = reason;
        this.startsAt = atColumnPrecision(startsAt);
        this.expiresAt = atColumnPrecision(expiresAt);
        this.revokedAt = atColumnPrecision(revokedAt);
        this.createdAt = atColumnPrecision(createdAt);
    }

    public static PremiumGrant create(UUID userId, String grantedBy, String reason, Instant expiresAt) {
        Instant now = Instant.now();
        return new PremiumGrant(UUID.randomUUID(), userId, grantedBy, reason, now, expiresAt, null, now);
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = atColumnPrecision(revokedAt);
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && !startsAt.isAfter(now) && expiresAt.isAfter(now);
    }

    private static Instant atColumnPrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
