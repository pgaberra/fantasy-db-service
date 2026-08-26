package com.fantasy.db.playerid.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What the remap did, or would have done. The unmapped counts are the ones worth reading before
 * applying: a row whose id the crosswalk has no entry for keeps it, and is then a row the app
 * can no longer draw.
 */
public record PlayerIdRemapResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Nothing was written")
        boolean dryRun,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int projectionsScanned,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RemapCounts playerRows,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RemapCounts draftPicks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sharesScanned,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The rows a share published — what its public page shows and what "
                        + "an import copies")
        RemapCounts sharedRows,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Distinct stored ids the crosswalk had no entry for, at most 50 of them")
        List<Integer> unmappedPlayerIds
) {

    @Schema(name = "PlayerIdRemapCounts")
    public record RemapCounts(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remapped,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unmapped
    ) {}
}
