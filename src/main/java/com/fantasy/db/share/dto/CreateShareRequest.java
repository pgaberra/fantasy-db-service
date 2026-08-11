package com.fantasy.db.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateShareRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The ranked rows to publish, in display order. The caller decides how many to include; the cap is a safety limit, not the product rule.")
        @NotNull @Valid @Size(max = 200) List<SharedPlayer> players
) {}
