package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a stored projection is for. {@code PROJECTION} is the one a user makes and edits;
 * {@code PRESET_DRAFT} backs a draft started from a preset, which needs somewhere to keep its
 * picks but is never listed as the user's own work — {@link ProjectionPreset} says which one;
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
     * Whether a user is limited in how many of this kind they may keep. Their own work is a
     * single thing, and so is their draft against any one preset — but there is more than one
     * preset, so {@code PRESET_DRAFT} is limited per preset rather than outright (see
     * {@code UserProjectionService.create}). Imported boards are not limited at all: there is no
     * reason to be able to draft against one friend's numbers but not two.
     */
    public boolean isUniquePerUser() {
        return this != IMPORTED;
    }
}
