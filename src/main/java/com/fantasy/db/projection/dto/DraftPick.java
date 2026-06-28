package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DraftPick(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String teamId
) {}
