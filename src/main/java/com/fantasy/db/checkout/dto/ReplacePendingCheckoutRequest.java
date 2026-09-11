package com.fantasy.db.checkout.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplacePendingCheckoutRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Payment provider id")
        @NotBlank @Size(max = 32) String provider,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The provider's id for the checkout, e.g. a Paddle transaction id")
        @NotBlank @Size(max = 255) String reference,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Where the browser pays this checkout")
        @NotBlank @Size(max = 2048) String checkoutUrl,

        @Schema(description = "The reference of the checkout this one replaces, as the caller last read it; "
                + "omit for the user's first. The store is refused with 409 if the stored checkout is "
                + "no longer that one")
        @Size(max = 255) String replacesReference
) {
}
