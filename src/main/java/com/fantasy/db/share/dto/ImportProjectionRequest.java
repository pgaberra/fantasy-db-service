package com.fantasy.db.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImportProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The share link's token.")
        @NotBlank @Size(max = 64) String token,

        @Schema(description = "What to call the copy. Defaults to the name it was shared under.")
        @Size(max = 100) String name
) {}
