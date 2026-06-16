package com.fantasy.db.passwordreset.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PasswordResetTokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt
) {}
