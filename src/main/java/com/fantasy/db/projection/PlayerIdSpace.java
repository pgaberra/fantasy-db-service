package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Which platform's player ids a stored row is keyed by.
 *
 * <p>Everything saved up to now is keyed by Yahoo's, because Yahoo was the only player source.
 * Yahoo stopped serving its player collection and ESPN provides the pool now, numbering the same
 * people differently, so the stored ids are remapped once. A row records which side of that it
 * is on: the two id spaces overlap in range, so without the marker a second remap could
 * translate an id that was never Yahoo's.
 */
public enum PlayerIdSpace {
    YAHOO("yahoo"),
    ESPN("espn");

    private final String code;

    PlayerIdSpace(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PlayerIdSpace fromCode(String code) {
        return Arrays.stream(values())
                .filter(space -> space.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player id space: " + code));
    }
}
