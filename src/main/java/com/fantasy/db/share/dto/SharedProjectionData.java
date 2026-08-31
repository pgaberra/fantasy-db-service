package com.fantasy.db.share.dto;

import com.fantasy.db.projection.dto.PositionOverride;
import com.fantasy.db.projection.dto.ProjectionSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The snapshot stored on a share: the league settings the ranking was produced under, the ranked
 * rows, and the positions the author had corrected by hand. Deliberately narrower than
 * {@code ProjectionData} — no draft state, and the settings arrive here with the Yahoo sync
 * details stripped, since neither belongs on a page anyone with the link can open.
 *
 * <p>The public page renders positions off each row, which already carry the author's
 * corrections. The overrides are carried separately because an import needs to tell a correction
 * from a position the read model reported, which the rows cannot say.
 */
public record SharedProjectionData(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionSettings settings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid List<SharedPlayer> players,
        @Schema(description = "Positions the author corrected by hand, so an import can inherit "
                + "them. Absent on a share published before shares carried them, and on one whose "
                + "author corrected nothing.")
        @Valid @Size(max = 2000) List<PositionOverride> positionOverrides
) {}
