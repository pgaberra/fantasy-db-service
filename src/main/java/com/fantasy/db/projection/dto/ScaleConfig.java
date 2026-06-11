package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ScaleConfig(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean scale,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull List<String> scalableStats
) {}
