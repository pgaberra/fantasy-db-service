package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum DraftTeam {
    ME("me"),
    OTHERS("others");

    private final String code;

    DraftTeam(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DraftTeam fromCode(String code) {
        return Arrays.stream(values())
                .filter(team -> team.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown draft team: " + code));
    }
}
