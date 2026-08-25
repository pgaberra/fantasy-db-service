package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerIdSpace;
import com.fantasy.db.projection.Season;
import com.fantasy.db.share.dto.SharedBoard;
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
 * not whatever the owner edited afterwards. It is also final — a published snapshot cannot be
 * refreshed or withdrawn, and sharing the same projection again returns the link it already has.
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private SharedBoard board;

    // Which platform's player ids the rows in `data` and `board` are keyed by. A share is
    // otherwise frozen; this is the one thing that may still be rewritten, because an id that no
    // longer resolves is not the picture that was shared either.
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
                           String name, Season season, SharedProjectionData data, SharedBoard board,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.projectionId = projectionId;
        this.userId = userId;
        this.token = token;
        this.name = name;
        this.season = season.getCode();
        this.data = data;
        this.board = board;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ProjectionShare create(UUID projectionId, UUID userId, String name,
                                         Season season, SharedProjectionData data, SharedBoard board) {
        Instant now = Instant.now();
        return new ProjectionShare(UUID.randomUUID(), projectionId, userId, generateToken(),
                name, season, data, board, now, now);
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

    public SharedBoard getBoard() {
        return board;
    }

    public PlayerIdSpace getPlayerIdSpace() {
        return PlayerIdSpace.fromCode(playerIdSpace);
    }

    /**
     * Replaces the snapshot's rows and records which platform's ids they are now keyed by. The
     * board goes with them: leaving it behind would hand an importer rows keyed by ids the
     * player pool no longer knows.
     */
    public void remapPlayerIds(SharedProjectionData remapped, SharedBoard remappedBoard, PlayerIdSpace space) {
        this.data = remapped;
        this.board = remappedBoard;
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
