package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.DraftStatus;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.ProjectionPreset;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ProjectionSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProjectionKind kind,
        @Schema(description = "Which preset a preset draft was started from. Absent on any other "
                + "kind, and on preset drafts stored before this was recorded.")
        ProjectionPreset preset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DraftStatus draftStatus,
        @Schema(description = "Who the board was copied from, on an imported projection. Absent on the user's own.")
        ProjectionOrigin origin
) {
    public static ProjectionSummaryResponse from(UserProjection projection) {
        return new ProjectionSummaryResponse(
                projection.getId().toString(),
                projection.getName(),
                projection.getKind(),
                projection.getPreset(),
                projection.getSeason(),
                projection.getCreatedAt(),
                projection.getUpdatedAt(),
                draftStatusOf(projection.getData()),
                ProjectionOrigin.from(projection));
    }

    private static DraftStatus draftStatusOf(ProjectionData data) {
        DraftState draft = data == null ? null : data.draft();
        if (draft == null) {
            return DraftStatus.NONE;
        }
        return draft.finishedAt() != null ? DraftStatus.FINISHED : DraftStatus.IN_PROGRESS;
    }
}
