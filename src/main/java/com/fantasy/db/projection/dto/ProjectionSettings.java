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
        @Schema(description = "Number of teams in the league; drives the category-league ranking pool size. Only relevant for category leagues — points leagues do not use it, so it is absent for them.")
        @Min(2) @Max(30) Integer leagueSize,
        @Schema(description = "Roster slots per team, used with leagueSize to size the category-league ranking pool (pool = teams × slots). Bench (bn) and utility (util) count as skater slots. Category leagues only — absent for points leagues.")
        @Valid RosterSlots rosterSlots,
        @Schema(description = "Minimum projected games a goalie must reach to qualify for category ranking; goalies below it are ranked last to avoid small-sample rate-stat inflation. Category leagues only — absent for points leagues.")
        @Min(0) @Max(82) Integer minGoalieGames,
        @Schema(description = "Where these settings were last synced from — the Yahoo league name/key and the timestamp. Absent if the projection was never synced from a Yahoo league.")
        @Valid YahooSync yahooSync
) {}
