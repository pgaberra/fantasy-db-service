package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.UserProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ProjectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static ProjectionResponse from(UserProjection projection) {
        return new ProjectionResponse(
                projection.getId().toString(),
                projection.getName(),
                projection.getData(),
                projection.getCreatedAt(),
                projection.getUpdatedAt());
    }
}
