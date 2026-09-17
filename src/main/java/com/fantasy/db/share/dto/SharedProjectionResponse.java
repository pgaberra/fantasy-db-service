package com.fantasy.db.share.dto;

import com.fantasy.db.projection.Season;
import com.fantasy.db.share.ProjectionShare;
import com.fantasy.db.share.SharedProjection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * What a visitor holding the link gets. Carries nothing that identifies the owner beyond what
 * they publish under — the alias they chose and the picture on their profile — no user id, no
 * projection id, no account details.
 */
public record SharedProjectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The owner's public name. Read live rather than snapshotted: a rename should follow onto links already shared.") String authorUsername,
        @Schema(description = "When the owner's profile picture last changed, or absent where they have none. The picture itself is served by GET /api/v1/shares/{token}/avatar; this says whether there is one to ask for and which version it is.") Instant authorAvatarUpdatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Season season,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SharedProjectionData data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static SharedProjectionResponse from(SharedProjection shared) {
        ProjectionShare share = shared.share();
        return new SharedProjectionResponse(
                share.getToken(),
                share.getName(),
                shared.authorUsername(),
                shared.authorAvatarUpdatedAt(),
                share.getSeason(),
                share.getData(),
                share.getCreatedAt(),
                share.getUpdatedAt());
    }
}
