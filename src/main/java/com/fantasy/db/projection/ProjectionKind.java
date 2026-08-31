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
     * Whether a user is limited in how many of this kind they may keep. Only {@code PRESET_DRAFT}
     * is: a draft against one preset is a single thing, though there is more than one preset, so
     * the limit is per preset rather than outright (see {@code UserProjectionService.create}).
     *
     * <p>The other two are unlimited. Imported boards always were — there is no reason to be able
     * to draft against one friend's numbers but not two — and a user's own work no longer is
     * either, now that a projection can be started from a copy of another one and kept beside it.
     * What still keeps two of them apart is the name, and the two share one namespace: they are
     * listed together and read by name, so {@code (user_id, name)} is unique across both. A
     * preset draft is outside that namespace, being named by the server and listed as nobody's
     * work — see V19 and {@code UserProjectionService.requireFreeName}.
     */
    public boolean isUniquePerUser() {
        return this == PRESET_DRAFT;
    }
}
