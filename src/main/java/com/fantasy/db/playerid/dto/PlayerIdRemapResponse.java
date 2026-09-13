package com.fantasy.db.playerid.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What the remap did, or would have done. The unmapped and colliding counts are the ones worth
 * reading before applying: a row whose id the crosswalk has no entry for keeps it, and is then a
 * row the app can no longer draw — unless that number is the one another player is being moved
 * to, in which case keeping it would show the row as him, so it is removed instead.
 */
public record PlayerIdRemapResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Nothing was written")
        boolean dryRun,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int projectionsScanned,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RemapCounts playerRows,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "A colliding pick is never removed, since that would rewrite the "
                        + "draft: any at all make an apply refuse")
        RemapCounts draftPicks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The positions an owner corrected by hand; an unmapped one stops "
                        + "reaching the player it was written for")
        RemapCounts positionOverrides,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sharesScanned,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The rows a share published — what its public page shows and what "
                        + "an import copies")
        RemapCounts sharedRows,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Distinct stored ids the crosswalk had no entry for and that stay, "
                        + "at most 50 of them")
        List<Integer> unmappedPlayerIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Distinct stored ids the crosswalk had no entry for that another "
                        + "player is being moved to, at most 50 of them")
        List<Integer> collidingPlayerIds
) {

    @Schema(name = "PlayerIdRemapCounts")
    public record RemapCounts(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remapped,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Not covered by the crosswalk, and kept on the old id")
            int unmapped,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Not covered by the crosswalk, on an id another player is being "
                            + "moved to, and removed on apply")
            int colliding
    ) {}
}
