package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a stored projection is for. {@code PROJECTION} is the one a user makes and edits;
 * {@code PRESET_DRAFT} backs a draft started from a preset (e.g. last season's stats), which
 * needs somewhere to keep its picks but is never listed as the user's own work.
 */
public enum ProjectionKind {
    PROJECTION("projection"),
    PRESET_DRAFT("preset_draft");

    private final String code;

    ProjectionKind(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
