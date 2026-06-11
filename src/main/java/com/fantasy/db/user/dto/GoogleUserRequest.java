package com.fantasy.db.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GoogleUserRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String googleSub
) {}
