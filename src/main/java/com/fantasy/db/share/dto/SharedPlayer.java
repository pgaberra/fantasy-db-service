package com.fantasy.db.share.dto;

import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.dto.PlayerStats;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One row of a shared snapshot. Identity (name, team, positions) and the computed rank and value
 * are denormalised on purpose: a share is a frozen picture, and the public page must render from
 * this row alone without loading the full player read model or recomputing a ranking whose pool
 * it no longer has.
 */
public record SharedPlayer(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
        @Schema(description = "NHL team abbreviation, absent for a player without one.")
        @Size(max = 10) String teamAbbrev,
        @Schema(description = "Player headshot URL, copied at share time so the public page needs no player read model. Absent for a player without one.")
        @Size(max = 300) String headshot,
        @Schema(description = "Eligible positions, for skaters. Absent for goalies.")
        @Size(max = 6) List<@Size(max = 4) String> positions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull PlayerType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Position in the shared ranking, 1-based.")
        @Min(1) int rank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The value the ranking was sorted by — fantasy points in a points league, z-score in a category league.")
        double value,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid PlayerStats stats
) {}
