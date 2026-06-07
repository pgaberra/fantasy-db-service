package com.fantasy.db.user.dto;

import com.fantasy.db.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String passwordHash
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId().toString(), user.getEmail(), user.getPasswordHash());
    }
}
