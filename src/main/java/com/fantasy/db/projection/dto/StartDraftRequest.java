package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Starting a draft against one of the user's own boards. The player rows are not sent: they are
 * copied from that board on the server, which is ~0.5 MB the caller would otherwise download and
 * upload again just to draft against numbers it already has.
 */
public record StartDraftRequest(
        @Schema(description = "What to call the draft. Defaults to the board's name. A name "
                + "another draft holds is numbered (\"Board (2)\") rather than refused.")
        @Size(max = 100) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The draft's own settings and its setup. `players` is ignored: the "
                        + "rows are copied from the board being drafted against.")
        @NotNull @Valid ProjectionData data
) {}
