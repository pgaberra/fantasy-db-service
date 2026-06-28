package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DraftTeam(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean mine
) {}
