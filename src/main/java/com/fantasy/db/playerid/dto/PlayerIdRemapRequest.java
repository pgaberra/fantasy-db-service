package com.fantasy.db.playerid.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The crosswalk to apply. It is computed by the caller, which is the only place that can see
 * both platforms' player pools; this service owns the stored rows and does the writing.
 */
public record PlayerIdRemapRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Old id → new id, one entry per player. Ids not listed are left "
                        + "exactly as they are.")
        @NotEmpty @Size(max = 5000) List<@Valid PlayerIdPair> mappings,
        @Schema(description = "Report what would change and write nothing. Defaults to true, so "
                + "an unqualified call cannot rewrite anyone's projection.")
        Boolean dryRun
) {

    public boolean isDryRun() {
        return dryRun == null || dryRun;
    }
}
