package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record DraftState(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid @Size(max = 32) List<DraftTeam> teams,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Size(max = 32) List<@NotBlank @Size(max = 64) String> order,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid List<DraftPick> picks,
        @Schema(description = "When the manager marked this draft finished (ISO-8601, UTC). Absent while the draft is still in progress.")
        Instant finishedAt
) {}
