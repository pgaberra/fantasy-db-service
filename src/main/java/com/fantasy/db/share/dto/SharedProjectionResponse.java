package com.fantasy.db.share.dto;

import com.fantasy.db.projection.Season;
import com.fantasy.db.share.ProjectionShare;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * What a visitor holding the link gets. Carries nothing that identifies the owner beyond the
 * alias they chose — no user id, no projection id, no account details.
 */
public record SharedProjectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The owner's public name. Read live rather than snapshotted: a rename should follow onto links already shared.") String authorUsername,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SharedProjectionData data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static SharedProjectionResponse from(ProjectionShare share, String authorUsername) {
        return new SharedProjectionResponse(
                share.getToken(),
                share.getName(),
                authorUsername,
                share.getSeason(),
                share.getData(),
                share.getCreatedAt(),
                share.getUpdatedAt());
    }
}
