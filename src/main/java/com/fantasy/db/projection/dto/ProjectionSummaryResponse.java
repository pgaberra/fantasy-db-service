package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.UserProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ProjectionSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static ProjectionSummaryResponse from(UserProjection projection) {
        return new ProjectionSummaryResponse(
                projection.getId().toString(),
                projection.getName(),
                projection.getCreatedAt(),
                projection.getUpdatedAt());
    }
}
