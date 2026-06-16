package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RosterSlots(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer c,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer lw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer rw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer d,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer util,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer bn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer g
) {}
