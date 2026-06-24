package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.DraftTeam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DraftPick(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DraftTeam by
) {}
