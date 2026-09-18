package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerIdSpace;
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
 * The public, read-only copy of one saved projection, reachable by an unguessable token.
 *
 * <p>A link follows its projection: sharing it again rewrites this row in place, under the same
 * token, and the owner's editor does that after every save. It is still a stored copy rather than
 * a view of the projection because the ranked rows can only be computed by the web, and the
 * public read needs them to filter, sort and cut the board for a visitor who is not signed in.
 * Deleting the projection deletes the share with it.
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

    // Which platform's player ids the rows in `data` are keyed by: the projection's, as of the last
    // publish, or whatever a remap has since translated them to.
    @Column(name = "player_id_space", nullable = false, length = 8)
    private String playerIdSpace = PlayerIdSpace.YAHOO.getCode();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectionShare() {
        // Required by JPA
    }

    public ProjectionShare(UUID id, UUID projectionId, UUID userId, String token,
                           String name, Season season, SharedProjectionData data,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.projectionId = projectionId;
        this.userId = userId;
        this.token = token;
        this.name = name;
        this.season = season.getCode();
        this.data = data;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * The id space is the shared projection's: the rows come from the same pool its rows are keyed
     * by. Left to the column default, every share published under ESPN's pool was stamped Yahoo,
     * which a remap would then translate as if its ids were Yahoo's.
     */
    public static ProjectionShare create(UUID projectionId, UUID userId, String name,
                                         Season season, SharedProjectionData data,
                                         PlayerIdSpace playerIdSpace) {
        Instant now = Instant.now();
        ProjectionShare share = new ProjectionShare(UUID.randomUUID(), projectionId, userId,
                generateToken(), name, season, data, now, now);
        share.playerIdSpace = playerIdSpace.getCode();
        return share;
    }

    /**
     * Publishes the projection again under the link it already has. Name, rows and id space are
     * all replaced together, since they are one copy taken at one moment; the token and the
     * season are not, so every link already posted keeps working.
     */
    public void refresh(String name, SharedProjectionData data, PlayerIdSpace playerIdSpace) {
        this.name = name;
        this.data = data;
        this.playerIdSpace = playerIdSpace.getCode();
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

    public String getName() {
        return name;
    }

    public Season getSeason() {
        return Season.fromCode(season);
    }

    public SharedProjectionData getData() {
        return data;
    }

    public PlayerIdSpace getPlayerIdSpace() {
        return PlayerIdSpace.fromCode(playerIdSpace);
    }

    /**
     * Replaces the snapshot's rows and records which platform's ids they are now keyed by. These
     * rows are also what an import copies, so an id the player pool no longer knows would follow
     * the board into the importer's account if it were left behind.
     */
    public void remapPlayerIds(SharedProjectionData remapped, PlayerIdSpace space) {
        this.data = remapped;
        this.playerIdSpace = space.getCode();
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
