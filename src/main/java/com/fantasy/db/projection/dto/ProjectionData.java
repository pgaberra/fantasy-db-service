package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectionData(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionSettings settings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "One row per player the projection covers. The cap is a safety "
                        + "limit sized above the largest player pool, not the product rule — a "
                        + "full board is every player in the league.")
        @NotNull @Valid @Size(max = 2000) List<PlayerProjection> players,
        @Schema(description = "In-draft state for this projection — the league's teams, the snake draft order, and the picks made so far (each attributed to a team) in pick order. Absent until a draft is configured.")
        @Valid DraftState draft,
        @Schema(description = "Positions the owner set by hand, replacing what the player read model reports for that skater. One entry per corrected player; absent or empty means every player keeps its reported positions. The cap matches the player list.")
        @Valid @Size(max = 2000) List<PositionOverride> positionOverrides
) {}
