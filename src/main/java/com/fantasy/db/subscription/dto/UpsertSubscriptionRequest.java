package com.fantasy.db.subscription.dto;

import com.fantasy.db.subscription.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpsertSubscriptionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Payment provider id, e.g. \"mock\" or \"stripe\"")
        @NotBlank @Size(max = 32) String provider,

        @Schema(description = "Provider-side customer id; null before the provider assigns one")
        @Size(max = 255) String providerCustomerId,

        @Schema(description = "Provider-side subscription id; null before the provider assigns one")
        @Size(max = 255) String providerSubscriptionId,

        @Schema(description = "Provider-side price/plan id; null if not applicable")
        @Size(max = 255) String priceId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull SubscriptionStatus status,

        @Schema(description = "When the current paid period ends; null for open-ended or incomplete states")
        Instant currentPeriodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the subscription is set to cancel at period end") boolean cancelAtPeriodEnd,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Provider event timestamp; an event older than the stored one is ignored")
        @NotNull Instant eventAt
) {
}
