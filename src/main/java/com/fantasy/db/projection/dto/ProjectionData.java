package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProjectionData(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionSettings settings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid List<PlayerProjection> players,
        @Schema(description = "In-draft state for this projection — the league's teams, the snake draft order, and the picks made so far (each attributed to a team) in pick order. Absent until a draft is configured.")
        @Valid DraftState draft
) {}
