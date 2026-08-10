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
        @Schema(description = "The name the owner chose to be credited as, if any.") String authorAlias,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SharedProjectionData data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static SharedProjectionResponse from(ProjectionShare share) {
        return new SharedProjectionResponse(
                share.getToken(),
                share.getName(),
                share.getAuthorAlias(),
                share.getSeason(),
                share.getData(),
                share.getCreatedAt(),
                share.getUpdatedAt());
    }
}
