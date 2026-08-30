package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which shared starting point a preset draft was started from.
 *
 * <p>{@link ProjectionKind#PRESET_DRAFT} records that a draft came from a preset but not which
 * one, and there is more than one. Matching them by name would work only while the names never
 * change; this says it outright, so a preset can be renamed without orphaning the drafts started
 * from it.
 *
 * <p>Null on a projection that is not a preset draft, and on preset drafts stored before this
 * was recorded — those were all {@link #LAST_SEASON}, and the migration that added the column
 * says so.
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
