package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which shared starting point a draft was started from.
 *
 * <p>{@link ProjectionKind#DRAFT} records that a row is a draft but not what it was drafted
 * against, and a draft can come from a preset or from one of the user's own boards. Matching
 * presets by name would work only while the names never change; this says it outright, so a
 * preset can be renamed without orphaning the drafts started from it.
 *
 * <p>Null on anything that is not a draft, on a draft started from a board (which records its
 * source projection instead), and on preset drafts stored before this was recorded — those were
 * all {@link #LAST_SEASON}, and the migration that added the column says so.
 */
public enum ProjectionPreset {

    /** Every player at last season's numbers. */
    LAST_SEASON("last_season"),

    /** Every player at the projection model's estimate for the coming season. */
    MODEL("model");

    private final String code;

    ProjectionPreset(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
