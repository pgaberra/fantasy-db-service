package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

public record RosterSlots(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer c,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer lw,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer rw,
        @Schema(description = "Wing flex slots (LW or RW). Absent reads as 0: boards saved before "
                + "the slot existed have none.") @Min(0) @Max(50) Integer w,
        @Schema(description = "Forward flex slots (C, LW or RW). Absent reads as 0: boards saved "
                + "before the slot existed have none.") @Min(0) @Max(50) Integer f,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer d,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer util,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer bn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(50) Integer g
) {

    public RosterSlots {
        w = Objects.requireNonNullElse(w, 0);
        f = Objects.requireNonNullElse(f, 0);
    }
}
