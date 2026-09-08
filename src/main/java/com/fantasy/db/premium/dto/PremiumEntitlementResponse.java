package com.fantasy.db.premium.dto;

import com.fantasy.db.premium.PremiumSource;
import com.fantasy.db.subscription.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PremiumEntitlementResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the user has premium access right now, paid for or granted")
        boolean premium,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What premium access rests on: a subscription, an admin grant, or both")
        PremiumSource source,

        @Schema(description = "Subscription status; null when the user has no subscription")
        SubscriptionStatus subscriptionStatus,

        @Schema(description = "When the current paid period ends; null when not applicable")
        Instant currentPeriodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the subscription is set to cancel at period end")
        boolean cancelAtPeriodEnd,

        @Schema(description = "When the admin grant runs out; null when there is no active grant")
        Instant grantExpiresAt,

        @Schema(description = "When premium runs out altogether; null when it is open-ended")
        Instant premiumUntil
) {
    public static PremiumEntitlementResponse none() {
        return new PremiumEntitlementResponse(false, PremiumSource.NONE, null, null, false, null, null);
    }
}
