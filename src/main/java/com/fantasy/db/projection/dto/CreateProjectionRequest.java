package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.ProjectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
        @Schema(description = "What the projection is for. Defaults to the user's own.")
        ProjectionKind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionData data
) {
    public ProjectionKind kindOrDefault() {
        return kind == null ? ProjectionKind.PROJECTION : kind;
    }
}
