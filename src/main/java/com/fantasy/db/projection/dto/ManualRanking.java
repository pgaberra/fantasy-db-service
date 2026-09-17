package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ManualRanking(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid PlayerTypeRanking skater,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid PlayerTypeRanking goalie
) {}
