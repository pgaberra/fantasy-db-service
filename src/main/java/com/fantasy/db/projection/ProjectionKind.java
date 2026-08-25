package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a stored projection is for. {@code PROJECTION} is the one a user makes and edits;
 * {@code PRESET_DRAFT} backs a draft started from a preset (e.g. last season's stats), which
 * needs somewhere to keep its picks but is never listed as the user's own work;
 * {@code IMPORTED} is a copy taken from someone's share link.
 */
public enum ProjectionKind {
    PROJECTION("projection"),
    PRESET_DRAFT("preset_draft"),
    IMPORTED("imported");

    private final String code;

    ProjectionKind(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * Whether a user may keep only one of this kind. Their own work and their preset draft are
     * each a single thing; imported boards are not — there is no reason to be able to draft
     * against one friend's numbers but not two.
     */
    public boolean isUniquePerUser() {
        return this != IMPORTED;
    }
}
