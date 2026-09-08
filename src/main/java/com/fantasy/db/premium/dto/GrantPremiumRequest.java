package com.fantasy.db.premium.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record GrantPremiumRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "When the grant stops giving premium access; must be in the future")
        @NotNull @Future Instant expiresAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Who handed the grant out, for the audit trail (the admin's email)")
        @NotBlank @Size(max = 255) String grantedBy,

        @Schema(description = "Free-text note on why premium was given")
        @Size(max = 255) String reason
) {
}
