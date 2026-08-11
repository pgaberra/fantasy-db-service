package com.fantasy.db.share.dto;

import com.fantasy.db.share.ProjectionShare;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The owner's view of a share: enough to show the link, its reach and when it was last refreshed.
 * The snapshot itself is not repeated back — the owner is looking at the live projection anyway.
 */
public record ShareResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String projectionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static ShareResponse from(ProjectionShare share) {
        return new ShareResponse(
                share.getId().toString(),
                share.getProjectionId().toString(),
                share.getToken(),
                share.getCreatedAt(),
                share.getUpdatedAt());
    }
}
