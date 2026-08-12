package com.fantasy.db.share.dto;

import com.fantasy.db.projection.dto.ProjectionSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The snapshot stored on a share: the league settings the ranking was produced under, plus the
 * ranked rows. Deliberately narrower than {@code ProjectionData} — no draft state, and the
 * settings arrive here with the Yahoo sync details stripped, since neither belongs on a page
 * anyone with the link can open.
 */
public record SharedProjectionData(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionSettings settings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid List<SharedPlayer> players
) {}
