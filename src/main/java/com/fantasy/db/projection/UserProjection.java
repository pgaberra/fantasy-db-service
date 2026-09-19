package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.DraftState;
import com.fantasy.db.projection.dto.ProjectionData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
/*
 * No uniqueConstraints here, deliberately. The rule is (user_id, name) over everything the user
 * names, and drafts and follows sit outside it — partial indexes, which JPA cannot express
 * (V19, narrowed by V25, split into two namespaces by V26). So is one follow per (user, share),
 * and so is the foreign key that takes a follow away with its share (V25).
 * Declaring the unfiltered version instead would put a constraint in the schema Hibernate builds
 * for tests that production does not have, and would forbid the one overlap that is allowed. The
 * rule lives in the migration, with UserProjectionService.requireFreeName saying it in code.
 */
@Table(name = "user_projections")
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
     * Which preset a {@link ProjectionKind#DRAFT} was started from. Null on every other
     * kind, and on preset drafts stored before the column existed — see V18.
     */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 20)
    private ProjectionPreset preset;

    /**
     * The board a {@link ProjectionKind#DRAFT} was copied from, where it came from one. Kept for
     * what it says rather than for what it holds: the draft carries its own copy of the numbers,
     * so the source may be edited, or deleted, without touching a draft under way — there is no
     * foreign key, and {@code UserProjectionService.delete} nulls it instead, so the tests and
     * production behave alike. Null on a draft against a preset.
     */
    @Column(name = "source_projection_id", updatable = false)
    private UUID sourceProjectionId;

    /**
     * Whether the name is still the one the server gave this row rather than one its owner typed.
     * A league sync renames a draft to the league's name, and only an auto-named one: a name
     * somebody chose themselves is more deliberate than the default it replaced, and a sync that
     * overwrote it would be destroying the more considered of the two.
     */
    @Column(name = "auto_named", nullable = false)
    private boolean autoNamed = true;

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
     * A draft against one of the user's own boards: a copy of that board's numbers with the
     * draft's own picks in it. A copy rather than a reference, so editing the board — or deleting
     * it — leaves a draft already under way exactly as it was, and so that ten drafts off one
     * board are ten independent boards.
     */
    public static UserProjection draftFrom(UserProjection source, String name, ProjectionData data) {
        Instant now = Instant.now();
        UserProjection draft = new UserProjection(UUID.randomUUID(), source.getUserId(), name,
                ProjectionKind.DRAFT, null, source.getSeason(), data, null, null, now, now);
        draft.sourceProjectionId = source.getId();
        draft.playerIdSpace = source.playerIdSpace;
        return draft;
    }

    /**
     * A follow of a share link: an imported board that keeps mirroring the author's, see
     * {@link #mirror}. The id space comes from the share rather than defaulting: the rows are a
     * copy of what was published, so a board already remapped to ESPN's numbering must not look to
     * a later remap pass like one still on Yahoo's.
     */
    public static UserProjection followOf(UUID userId, String name, Season season, ProjectionData data,
                                          String shareToken, String authorUsername,
                                          PlayerIdSpace playerIdSpace) {
        Instant now = Instant.now();
        UserProjection follow = new UserProjection(UUID.randomUUID(), userId, name,
                ProjectionKind.IMPORTED, null, season, data, shareToken, authorUsername, now, now);
        follow.playerIdSpace = playerIdSpace.getCode();
        return follow;
    }

    /**
     * Whether this row follows a share link. Only an import stamped with the link it came from
     * does; a spreadsheet import is {@code IMPORTED} too but came from no link, and is the user's
     * own to edit.
     */
    public boolean isFollow() {
        return kind == ProjectionKind.IMPORTED && originShareToken != null;
    }

    /**
     * Renames the row. {@code autoNamed} says who chose the name: false for one its owner typed,
     * true for one the server derived (a draft named after the league it was just synced with),
     * which stays open to being derived again.
     */
    public void rename(String name, boolean autoNamed) {
        this.name = name;
        this.autoNamed = autoNamed;
        this.updatedAt = Instant.now();
    }

    public void update(String name, ProjectionData data) {
        this.name = name;
        this.data = data;
        this.updatedAt = Instant.now();
    }

    /**
     * Takes the author's board as it now stands, under the name they now give it, and keeps the
     * follower's own draft: the board is the author's, the picks made against it are not.
     */
    public void mirror(String name, ProjectionData board, PlayerIdSpace space) {
        this.name = name;
        this.data = new ProjectionData(board.settings(), board.players(), data.draft(),
                board.positionOverrides());
        this.playerIdSpace = space.getCode();
        this.updatedAt = Instant.now();
    }

    /**
     * The one thing a follower may change on a follow. The stamp moves only when the draft does,
     * since the list orders by it and an unchanged save is not an edit.
     */
    public void updateDraft(DraftState draft) {
        if (Objects.equals(draft, data.draft())) {
            return;
        }
        this.data = new ProjectionData(data.settings(), data.players(), draft,
                data.positionOverrides());
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

    public UUID getSourceProjectionId() {
        return sourceProjectionId;
    }

    public boolean isAutoNamed() {
        return autoNamed;
    }

    /** Forgets a source board that has been deleted, leaving the draft itself untouched. */
    public void clearSourceProjection() {
        this.sourceProjectionId = null;
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
