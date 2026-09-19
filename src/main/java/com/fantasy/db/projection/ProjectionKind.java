package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a stored projection is for. {@code PROJECTION} is one a user makes and edits;
 * {@code IMPORTED} is a copy taken from someone's share link; {@code DRAFT} is a board drafted
 * against, which holds a copy of the numbers it was started from together with its picks.
 *
 * <p>A draft is a row of its own rather than a field on the board it came from, which is what
 * lets a user run as many drafts against one board as they like — ten mocks off the same
 * projection are ten rows. It carries its own name, and where it came from is recorded by
 * {@link ProjectionPreset} (a preset) or the source projection id (a board).
 */
public enum ProjectionKind {
    PROJECTION("projection"),
    DRAFT("draft"),
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
     * Which naming namespace this kind lives in. There are two, and a name has to be distinct
     * only within its own: the boards a user keeps (their own work and what they imported) are
     * listed together and read by name, and so are their drafts — but a draft is named after the
     * board it was started from, so the two lists would collide on their very first name if they
     * shared one namespace. See V25 and {@code UserProjectionService.requireFreeName}.
     */
    public boolean isDraft() {
        return this == DRAFT;
    }
}
