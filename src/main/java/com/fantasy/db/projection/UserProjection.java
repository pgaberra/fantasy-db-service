package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.ProjectionData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_projections",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_projections_user_kind_name",
                columnNames = {"user_id", "kind", "name"}))
public class UserProjection {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ProjectionKind kind;

    /**
     * Which preset a {@link ProjectionKind#PRESET_DRAFT} was started from. Null on every other
     * kind, and on preset drafts stored before the column existed — see V18.
     */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 20)
    private ProjectionPreset preset;

    // Stored as the season's 8-digit code (e.g. "20262027"); exposed as the Season enum.
    // Kept as a plain String column so Hibernate doesn't auto-generate an enum CHECK
    // constraint on the constant names (which wouldn't match the stored codes).
    @Column(nullable = false, updatable = false)
    private String season;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ProjectionData data;

    @Column(name = "origin_share_token", updatable = false, length = 64)
    private String originShareToken;

    @Column(name = "origin_author_username", updatable = false, length = 20)
    private String originAuthorUsername;

    // Which platform's player ids the rows in `data` are keyed by. Stored as the code rather
    // than as a mapped enum, for the same reason as `season`.
    @Column(name = "player_id_space", nullable = false, length = 8)
    private String playerIdSpace = PlayerIdSpace.YAHOO.getCode();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProjection() {
        // Required by JPA
    }

    public UserProjection(UUID id, UUID userId, String name, ProjectionKind kind,
                          ProjectionPreset preset, Season season,
                          ProjectionData data, String originShareToken, String originAuthorUsername,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.kind = kind;
        this.preset = preset;
        this.season = season.getCode();
        this.data = data;
        this.originShareToken = originShareToken;
        this.originAuthorUsername = originAuthorUsername;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserProjection create(UUID userId, String name, ProjectionKind kind,
                                        ProjectionPreset preset, Season season,
                                        ProjectionData data, PlayerIdSpace playerIdSpace) {
        Instant now = Instant.now();
        UserProjection projection = new UserProjection(
                UUID.randomUUID(), userId, name, kind, preset, season, data, null, null, now, now);
        projection.playerIdSpace = playerIdSpace.getCode();
        return projection;
    }

    /**
     * The id space comes from the share rather than defaulting: the rows are a copy of what was
     * published, so a board already remapped to ESPN's numbering must not look to a later remap
     * pass like one still on Yahoo's.
     */
    public static UserProjection importedFrom(UUID userId, String name, Season season, ProjectionData data,
                                              String shareToken, String authorUsername,
                                              PlayerIdSpace playerIdSpace) {
        Instant now = Instant.now();
        UserProjection imported = new UserProjection(UUID.randomUUID(), userId, name,
                ProjectionKind.IMPORTED, null, season, data, shareToken, authorUsername, now, now);
        imported.playerIdSpace = playerIdSpace.getCode();
        return imported;
    }

    public void update(String name, ProjectionData data) {
        this.name = name;
        this.data = data;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public ProjectionKind getKind() {
        return kind;
    }

    public ProjectionPreset getPreset() {
        return preset;
    }

    public Season getSeason() {
        return Season.fromCode(season);
    }

    public ProjectionData getData() {
        return data;
    }

    public String getOriginShareToken() {
        return originShareToken;
    }

    public String getOriginAuthorUsername() {
        return originAuthorUsername;
    }

    public PlayerIdSpace getPlayerIdSpace() {
        return PlayerIdSpace.fromCode(playerIdSpace);
    }

    /** Replaces the rows and records which platform's ids they are now keyed by. */
    public void remapPlayerIds(ProjectionData remapped, PlayerIdSpace space) {
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
