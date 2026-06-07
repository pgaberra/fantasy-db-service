package com.fantasy.db.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExistsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exists
) {}
