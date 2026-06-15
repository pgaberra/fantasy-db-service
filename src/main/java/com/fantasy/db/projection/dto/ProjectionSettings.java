package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.ScoringType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record ProjectionSettings(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScoringType scoringType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Map<String, Double> statWeights,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<String> activeScoringColumns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<String> activeUtilityColumns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid Map<String, ScaleConfig> scaleSettings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Map<String, Integer> decimalSettings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean useDefaultDecimals,
        @Schema(description = "Number of teams in the league; drives the ranking pool size. Optional — older projections without it default to 12.")
        @Min(2) @Max(30) Integer leagueSize
) {}
