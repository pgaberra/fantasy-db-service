package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProjectionData(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionSettings settings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid List<PlayerProjection> players
) {}
