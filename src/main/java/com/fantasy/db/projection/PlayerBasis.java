package com.fantasy.db.projection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * What a projection's player rows started from, and therefore what a player who joins the pool
 * later should be seeded with. {@code LAST_SEASON} means the rows began as the cached stat line
 * of the season before; {@code BLANK} means they began at zero. Absent on projections saved
 * before this was recorded — the caller that reconciles the rows against the player pool infers
 * it once and stores the answer.
 */
public enum PlayerBasis {
    LAST_SEASON("last_season"),
    BLANK("blank");

    private final String code;

    PlayerBasis(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PlayerBasis fromCode(String code) {
        return Arrays.stream(values())
                .filter(basis -> basis.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player basis: " + code));
    }
}
