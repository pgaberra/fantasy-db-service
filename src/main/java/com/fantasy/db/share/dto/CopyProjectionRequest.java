package com.fantasy.db.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Taking a copy of a shared board. The copy is named by the server ("Copy of <the share's
 * name>"), for the same reason a follow is: the reader pressed a button on someone's board, and
 * naming it is something they can do afterwards on a board that is already saved.
 */
public record CopyProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The share link's token.")
        @NotBlank @Size(max = 64) String token,

        @Schema(description = "The share's `updatedAt` as the reader last saw it. Sent, and the "
                + "board has changed since, the copy is refused with 412, so nobody is handed a "
                + "board they never looked at. Left out, the copy is of the board as it is now.")
        Instant seenUpdatedAt
) {}
