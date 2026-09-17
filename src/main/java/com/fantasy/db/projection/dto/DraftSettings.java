package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.ScoringType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * The league a draft is ranked by, held by the draft itself so that setting it up never changes
 * the projection the draft is played against. The league half of {@link ProjectionSettings}: how
 * it scores, on which stats, its size and roster, and where it was imported from.
 */
public record DraftSettings(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ScoringType scoringType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 100) Map<@NotBlank @Size(max = 32) String, @NotNull Double> statWeights,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 32) String> activeScoringColumns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 32) String> activeUtilityColumns,
        @Schema(description = "Number of teams in the league. Category leagues only — absent for points leagues.")
        @Min(2) @Max(30) Integer leagueSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Roster slots per team. The draft's rosters are built from them in both league types.")
        @NotNull @Valid RosterSlots rosterSlots,
        @Schema(description = "Minimum projected games a goalie must reach to qualify for category ranking. Category leagues only — absent for points leagues. Capped at a full 84-game season.")
        @Min(0) @Max(84) Integer minGoalieGames,
        @Schema(description = "The Yahoo league these settings were imported from, if any.")
        @Valid YahooSync yahooSync,
        @Schema(description = "The ESPN league these settings were imported from, if any.")
        @Valid EspnSync espnSync,
        @Schema(description = "The last ESPN league imported from, kept so a re-import does not have to be retyped.")
        @Size(max = 50) String lastEspnLeagueId
) {}
