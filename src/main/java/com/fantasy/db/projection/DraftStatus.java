package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DraftStatus {
    NONE("none"),
    IN_PROGRESS("in_progress"),
    FINISHED("finished");

    private final String code;

    DraftStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
