package com.fantasy.db.premium.dto;

import com.fantasy.db.premium.PremiumGrant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PremiumGrantResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt,
        @Schema(description = "When the grant was revoked; null while it still stands") Instant revokedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String grantedBy,
        @Schema(description = "Free-text note on why premium was given") String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the grant gives premium access right now") boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {
    public static PremiumGrantResponse from(PremiumGrant grant, Instant now) {
        return new PremiumGrantResponse(
                grant.getId().toString(),
                grant.getUserId().toString(),
                grant.getStartsAt(),
                grant.getExpiresAt(),
                grant.getRevokedAt(),
                grant.getGrantedBy(),
                grant.getReason(),
                grant.isActive(now),
                grant.getCreatedAt());
    }
}
