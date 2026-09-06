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

    @JsonCreator
    public static SubscriptionStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown subscription status code: " + code));
    }
}
