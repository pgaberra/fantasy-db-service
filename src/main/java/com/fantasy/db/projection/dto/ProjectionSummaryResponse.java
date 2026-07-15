package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.DraftStatus;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ProjectionSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DraftStatus draftStatus
) {
    public static ProjectionSummaryResponse from(UserProjection projection) {
        return new ProjectionSummaryResponse(
                projection.getId().toString(),
                projection.getName(),
                projection.getSeason(),
                projection.getCreatedAt(),
                projection.getUpdatedAt(),
                draftStatusOf(projection.getData()));
    }

    private static DraftStatus draftStatusOf(ProjectionData data) {
        DraftState draft = data == null ? null : data.draft();
        if (draft == null) {
            return DraftStatus.NONE;
        }
        return draft.finishedAt() != null ? DraftStatus.FINISHED : DraftStatus.IN_PROGRESS;
    }
}
