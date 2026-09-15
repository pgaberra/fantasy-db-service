package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.PlayerBasis;
import com.fantasy.db.projection.ScoringType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
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
        @Valid YahooSync yahooSync,
        @Schema(description = "Where these settings were last synced from — the ESPN league name/id and the timestamp. Absent if the projection was never synced from an ESPN league.")
        @Valid EspnSync espnSync,
        @Schema(description = "The last ESPN league this projection imported from, kept after the settings stop being the league's so a re-import does not have to be retyped. Outlives espnSync, which is cleared when the user takes the projection out of sync.")
        @Size(max = 50) String lastEspnLeagueId,
        @Schema(description = "What the player rows started from, and so what a player who joins the pool later is seeded with: last season's stat line, or zeros. Absent on projections saved before this was recorded.")
        PlayerBasis playerBasis,
        @Schema(description = "When the player rows were last reconciled against the player pool — the finish time of the sync run they were reconciled against. Absent until the rows have been reconciled once.")
        Instant playerPoolSyncedAt,
        @Schema(description = "Players a reconciliation with the player pool added whom the owner has not acknowledged yet, kept so the app can go on saying so until they do. Absent once acknowledged, and on a projection nothing was ever added to. The cap matches the player list.")
        @Size(max = 2000) List<Integer> unacknowledgedNewPlayerIds
) {

    /** These settings with a different set of unacknowledged new players. */
    public ProjectionSettings withUnacknowledgedNewPlayerIds(List<Integer> playerIds) {
        return new ProjectionSettings(scoringType, statWeights, activeScoringColumns,
                activeUtilityColumns, scaleSettings, decimalSettings, useDefaultDecimals, leagueSize,
                rosterSlots, minGoalieGames, yahooSync, espnSync, lastEspnLeagueId, playerBasis,
                playerPoolSyncedAt, playerIds);
    }
}
