package com.fantasy.db.checkout.dto;

import com.fantasy.db.checkout.PendingCheckout;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PendingCheckoutResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Payment provider id") String provider,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The provider's id for the checkout, e.g. a Paddle transaction id") String reference,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Where the browser pays this checkout")
        String checkoutUrl,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "When this checkout was stored")
        Instant updatedAt
) {
    public static PendingCheckoutResponse from(PendingCheckout checkout) {
        return new PendingCheckoutResponse(
                checkout.getProvider(), checkout.getReference(), checkout.getCheckoutUrl(), checkout.getUpdatedAt());
    }
}
