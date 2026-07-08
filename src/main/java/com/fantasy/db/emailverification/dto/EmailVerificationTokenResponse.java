package com.fantasy.db.emailverification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record EmailVerificationTokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt
) {}
