package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fantasy.db.projection.UserProjection;

/**
 * The link a projection follows, and who published it. Present exactly on a follow: the board is
 * the author's, mirrored in whenever they publish, and the only thing its owner may change on it
 * is the draft. Absent on a projection the user made themselves and on a copy they took of
 * someone's board, both of which are theirs to edit.
 */
public record ProjectionOrigin(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The share this board follows. Never stale: the follow goes away with the share (V25).")
        String shareToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The author's public name as it read when the link was first followed. Snapshotted, unlike the live name on the public page.")
        String authorUsername
) {
    public static ProjectionOrigin from(UserProjection projection) {
        String token = projection.getOriginShareToken();
        return token == null
                ? null
                : new ProjectionOrigin(token, projection.getOriginAuthorUsername());
    }
}
