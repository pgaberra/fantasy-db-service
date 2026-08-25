package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ProjectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectionKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectionData data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(description = "Who the board was copied from, on an imported projection. Absent on the user's own.")
        ProjectionOrigin origin
) {
    public static ProjectionResponse from(UserProjection projection) {
        return new ProjectionResponse(
                projection.getId().toString(),
                projection.getName(),
                projection.getKind(),
                projection.getSeason(),
                projection.getData(),
                projection.getCreatedAt(),
                projection.getUpdatedAt(),
                ProjectionOrigin.from(projection));
    }
}
