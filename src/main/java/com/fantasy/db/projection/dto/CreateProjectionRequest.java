package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.PlayerIdSpace;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.ProjectionPreset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
        @Schema(description = "What the projection is for. Defaults to the user's own.")
        ProjectionKind kind,
        @Schema(description = "Which preset a draft was started from. Ignored on any other kind, "
                + "and left unset on a draft started from one of the user's own boards — that "
                + "one is created through POST /projections/{id}/drafts instead.")
        ProjectionPreset preset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionData data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Which platform's player ids the rows are keyed by. Required rather "
                        + "than defaulted: a wrong value here is silent until a remap translates "
                        + "ids that were never in the space it assumed.")
        @NotNull PlayerIdSpace playerIdSpace
) {
    public ProjectionKind kindOrDefault() {
        return kind == null ? ProjectionKind.PROJECTION : kind;
    }
}
