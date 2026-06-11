package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PlayerStats(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Map<String, Double> utility,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Map<String, Double> scoring
) {}
