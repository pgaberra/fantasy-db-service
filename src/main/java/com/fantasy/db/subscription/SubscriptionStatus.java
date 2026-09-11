package com.fantasy.db.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum SubscriptionStatus {
    ACTIVE("active"),
    TRIALING("trialing"),
    PAST_DUE("past_due"),
    CANCELED("canceled"),
    PAUSED("paused"),
    UNPAID("unpaid"),
    INCOMPLETE("incomplete");

    private final String code;

    SubscriptionStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * Whether a subscription in this state can still bill or come back: everything except one
     * that has ended ({@code canceled}) or never started ({@code incomplete}). A user holds at
     * most one of these, since two would mean paying, or being made to pay, twice.
     */
    public boolean isLive() {
        return this == ACTIVE || this == TRIALING || this == PAST_DUE || this == PAUSED || this == UNPAID;
    }

    @JsonCreator
    public static SubscriptionStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription status code: " + code));
    }
}
