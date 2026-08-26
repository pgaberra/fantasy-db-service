package com.fantasy.db.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateShareRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The ranked rows to publish, in display order. A share carries the "
                        + "whole board so a signed-in visitor can read all of it; the cap is a "
                        + "safety limit sized above the largest player pool, not the product rule.")
        @NotNull @Valid @Size(max = 2000) List<SharedPlayer> players
) {}
