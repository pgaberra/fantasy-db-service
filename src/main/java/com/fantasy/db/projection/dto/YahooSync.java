package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record YahooSync(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200) String leagueName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String leagueKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Instant syncedAt
) {}
