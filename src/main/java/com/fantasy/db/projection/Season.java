package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Season {
    SEASON_2025_2026("20252026"),
    SEASON_2026_2027("20262027"),
    SEASON_2027_2028("20272028"),
    SEASON_2028_2029("20282029");

    private final String code;

    Season(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static Season fromCode(String code) {
        return Arrays.stream(values())
                .filter(season -> season.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown season code: " + code));
    }
}
