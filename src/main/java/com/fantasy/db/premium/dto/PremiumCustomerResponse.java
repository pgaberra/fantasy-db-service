package com.fantasy.db.premium.dto;

import com.fantasy.db.premium.PremiumSource;
import com.fantasy.db.subscription.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PremiumCustomerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(description = "The account's public name; null until the user picks one") String username,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What premium access rests on: a subscription, an admin grant, or both")
        PremiumSource source,

        @Schema(description = "Subscription status; null when the user has no subscription")
        SubscriptionStatus subscriptionStatus,

        @Schema(description = "Payment provider id; null when there is no subscription") String provider,

        @Schema(description = "When the current paid period ends; null when not applicable")
        Instant currentPeriodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the subscription is set to cancel at period end")
        boolean cancelAtPeriodEnd,

        @Schema(description = "When the admin grant runs out; null when there is no active grant")
        Instant grantExpiresAt,

        @Schema(description = "Who handed the grant out; null when there is no active grant")
        String grantedBy,

        @Schema(description = "Why premium was given; null when there is no active grant or no note given")
        String grantReason,

        @Schema(description = "When premium runs out altogether; null when it is open-ended")
        Instant premiumUntil,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "When the account was created")
        Instant userCreatedAt
) {
}
