package com.fantasy.db.premium;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PremiumSource {
    NONE("none"),
    SUBSCRIPTION("subscription"),
    GRANT("grant"),
    BOTH("both");

    private final String code;

    PremiumSource(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PremiumSource fromCode(String code) {
        return Arrays.stream(values())
                .filter(source -> source.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown premium source code: " + code));
    }
}
