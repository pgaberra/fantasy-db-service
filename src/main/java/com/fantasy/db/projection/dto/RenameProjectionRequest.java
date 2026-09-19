package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Renaming a saved projection or a draft, without sending the board along with it.
 */
public record RenameProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
        @Schema(description = "True when the name was derived by the app rather than typed by the "
                + "user — a draft taking the name of the league it was just synced with. Such a "
                + "rename is skipped where the user has named the row themselves, and a name "
                + "another row holds is numbered rather than refused. Defaults to false.")
        Boolean derived
) {
    public boolean derivedOrDefault() {
        return Boolean.TRUE.equals(derived);
    }
}
