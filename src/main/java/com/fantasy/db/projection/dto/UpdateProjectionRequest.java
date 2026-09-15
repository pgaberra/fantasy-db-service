package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.PlayerIdSpace;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProjectionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid UpdateProjectionData data,
        @Schema(description = "The numbering the caller's rows are keyed by. When given and the stored "
                + "projection is keyed by another, nothing is written (409): those rows came from "
                + "a different platform's pool and would replace the stored ones with the wrong players.")
        PlayerIdSpace playerIdSpace
) {}
