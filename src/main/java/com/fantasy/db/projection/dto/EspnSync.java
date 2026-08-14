package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * An ESPN league a projection's settings were synced from. Kept separate from {@link YahooSync}
 * rather than generalised into one field: the two platforms identify a league differently (Yahoo's
 * opaque league key, ESPN's numeric id), and a projection can only carry one sync at a time, so
 * which field is set is also what says where the settings came from.
 */
public record EspnSync(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200) String leagueName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50) String leagueId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Instant syncedAt
) {}
