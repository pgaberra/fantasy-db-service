package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.SkaterPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Positions the owner set by hand for one skater, replacing the ones the player read model reports.")
public record PositionOverride(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int playerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @NotEmpty @Size(max = 4)
                List<SkaterPosition> positions
) {}
