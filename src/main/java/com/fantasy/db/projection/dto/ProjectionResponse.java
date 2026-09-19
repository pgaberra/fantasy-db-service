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
        @Schema(description = "The share link this board follows, and who published it. Present "
                + "only on a follow, which is read-only apart from its draft. Absent on the "
                + "user's own boards, including a copy taken from a link.")
        ProjectionOrigin origin,
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
                projection.getPlayerIdSpace());
    }
}
