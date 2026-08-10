package com.fantasy.db.share;

import com.fantasy.db.projection.Season;
import com.fantasy.db.share.dto.SharedProjectionData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * A public, read-only snapshot of one saved projection, reachable by an unguessable token.
 *
 * <p>The snapshot is deliberate: a link posted somewhere public keeps showing what was shared,
 * not whatever the owner edited afterwards. Re-sharing refreshes the snapshot but keeps the
 * token, so links already in the wild stay valid; unsharing deletes the row, so a later share
 * mints a new token and the old link is dead for good.
 */
@Entity
@Table(name = "projection_shares")
public class ProjectionShare {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 16;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "projection_id", nullable = false, updatable = false)
    private UUID projectionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 64)
    private String token;

    @Column(name = "author_alias", length = 40)
    private String authorAlias;

    @Column(nullable = false)
    private String name;

    // Copied from the projection at share time and stored as the 8-digit code, for the same
    // reason as UserProjection.season: a plain String column keeps Hibernate from generating an
    // enum CHECK constraint on the constant names.
    @Column(nullable = false, updatable = false)
    private String season;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private SharedProjectionData data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectionShare() {
        // Required by JPA
    }

    public ProjectionShare(UUID id, UUID projectionId, UUID userId, String token, String authorAlias,
                           String name, Season season, SharedProjectionData data,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.projectionId = projectionId;
        this.userId = userId;
        this.token = token;
        this.authorAlias = authorAlias;
        this.name = name;
        this.season = season.getCode();
        this.data = data;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ProjectionShare create(UUID projectionId, UUID userId, String authorAlias, String name,
                                         Season season, SharedProjectionData data) {
        Instant now = Instant.now();
        return new ProjectionShare(UUID.randomUUID(), projectionId, userId, generateToken(), authorAlias,
                name, season, data, now, now);
    }

    public void refresh(String authorAlias, String name, SharedProjectionData data) {
        this.authorAlias = authorAlias;
        this.name = name;
        this.data = data;
        this.updatedAt = Instant.now();
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectionId() {
        return projectionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public String getAuthorAlias() {
        return authorAlias;
    }

    public String getName() {
        return name;
    }

    public Season getSeason() {
        return Season.fromCode(season);
    }

    public SharedProjectionData getData() {
        return data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
