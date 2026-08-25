package com.fantasy.db.playerid.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/** One player, as the old platform numbered them and as the new one does. */
public record PlayerIdPair(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The stored id")
        @Min(1) int from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The id to store instead")
        @Min(1) int to
) {}
