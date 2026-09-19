package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.PlayerIdSpace;
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
        ProjectionOrigin origin,
        @Schema(description = "The board a draft was started from. Absent on anything that is not "
                + "a draft, on a draft started from a preset, and once that board is deleted — "
                + "the draft holds its own copy of the numbers and outlives it.")
        String sourceProjectionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the name is still the one the server gave this row. False "
                        + "once its owner has named it, which a derived rename then leaves alone.")
        boolean autoNamed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Which platform's numbering the player ids in data are. A caller "
                        + "serving another platform's pool must not merge that pool into these rows.")
        PlayerIdSpace playerIdSpace
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
                ProjectionOrigin.from(projection),
                projection.getSourceProjectionId() == null
                        ? null : projection.getSourceProjectionId().toString(),
                projection.isAutoNamed(),
                projection.getPlayerIdSpace());
    }
}
