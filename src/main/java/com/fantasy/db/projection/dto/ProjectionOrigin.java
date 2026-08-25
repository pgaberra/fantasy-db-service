package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fantasy.db.projection.UserProjection;

/**
 * Who a projection was copied from, on the copies that were imported from a share link. Absent on
 * a projection the user made themselves.
 */
public record ProjectionOrigin(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The share the board was copied from. Still set once that share is gone, so a stale token here is expected.")
        String shareToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The author's public name as it read at import time. Snapshotted with the rows, unlike the live name on the public page.")
        String authorUsername
) {
    public static ProjectionOrigin from(UserProjection projection) {
        String token = projection.getOriginShareToken();
        return token == null
                ? null
                : new ProjectionOrigin(token, projection.getOriginAuthorUsername());
    }
}
