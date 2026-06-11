package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.PlayerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PlayerProjection(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull PlayerType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid PlayerStats stats
) {}
