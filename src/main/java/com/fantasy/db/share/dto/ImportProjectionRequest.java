package com.fantasy.db.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Following a share link. There is no name: a follow is named after the share and renamed with
 * it, so there is nothing for the follower to choose and nothing of theirs for it to clash with.
 */
public record ImportProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The share link's token.")
        @NotBlank @Size(max = 64) String token,

        @Schema(description = "The share's `updatedAt` as the reader last saw it. Sent, and the "
                + "board has changed since, the request is refused with 412, so nobody starts "
                + "following a board they never looked at. Left out, the board as it is now is "
                + "followed.")
        Instant seenUpdatedAt
) {}
