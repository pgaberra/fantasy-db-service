package com.fantasy.db.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateShareRequest(
        @Schema(description = "Name the owner wants to be credited as on the public page. Absent means the page credits nobody — the account's email is never shown.")
        @Size(max = 40) String authorAlias,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The ranked rows to publish, in display order. The caller decides how many to include; the cap is a safety limit, not the product rule.")
        @NotNull @Valid @Size(max = 200) List<SharedPlayer> players
) {}
