package com.fantasy.db.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ImportProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The share link's token.")
        @NotBlank @Size(max = 64) String token,

        @Schema(description = "What to call the copy. Defaults to the name it was shared under.")
        @Size(max = 100) String name,

        @Schema(description = "The share's `updatedAt` as the reader last saw it. Sent, and the "
                + "board has changed since, the copy is refused with 412, so nobody is handed a "
                + "board they never looked at. Left out, the copy is of the board as it is now.")
        Instant seenUpdatedAt
) {}
