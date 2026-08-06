package com.fantasy.db.subscription.dto;

import com.fantasy.db.subscription.Subscription;
import com.fantasy.db.subscription.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record SubscriptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SubscriptionStatus status,

        @Schema(description = "When the current paid period ends; null for open-ended or incomplete states")
        Instant currentPeriodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the subscription is set to cancel at period end") boolean cancelAtPeriodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the subscription currently grants premium access") boolean premium,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Payment provider id") String provider,

        @Schema(description = "Provider-side customer id; null before the provider assigns one")
        String providerCustomerId
) {
    public static SubscriptionResponse from(Subscription subscription, Instant now) {
        return new SubscriptionResponse(
                subscription.getStatus(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                subscription.isPremium(now),
                subscription.getProvider(),
                subscription.getProviderCustomerId());
    }
}
