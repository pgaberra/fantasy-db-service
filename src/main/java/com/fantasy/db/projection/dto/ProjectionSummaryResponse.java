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
        @Schema(description = "Which preset a draft was started from. Absent on any other kind, "
                + "on a draft started from one of the user's own boards, and on preset drafts "
                + "stored before this was recorded.")
        ProjectionPreset preset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DraftStatus draftStatus,
        @Schema(description = "The share link this board follows, and who published it. Present "
                + "only on a follow, which is read-only apart from its draft. Absent on the "
                + "user's own boards, including a copy taken from a link.")
        ProjectionOrigin origin,
        @Schema(description = "The board a draft was started from. Absent on anything that is not "
                + "a draft, on a draft started from a preset, and once that board is deleted — "
                + "the draft holds its own copy of the numbers and outlives it.")
        String sourceProjectionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the name is still the one the server gave this row. False "
                        + "once its owner has named it, which a derived rename then leaves alone.")
        boolean autoNamed
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
                ProjectionOrigin.from(projection),
                projection.getSourceProjectionId() == null
                        ? null : projection.getSourceProjectionId().toString(),
                projection.isAutoNamed());
    }

    private static DraftStatus draftStatusOf(ProjectionData data) {
        DraftState draft = data == null ? null : data.draft();
        if (draft == null) {
            return DraftStatus.NONE;
        }
        return draft.finishedAt() != null ? DraftStatus.FINISHED : DraftStatus.IN_PROGRESS;
    }
}
