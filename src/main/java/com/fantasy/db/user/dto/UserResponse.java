package com.fantasy.db.user.dto;

import com.fantasy.db.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(description = "Null for users who only authenticate via Google") String passwordHash,
        @Schema(description = "Google subject id; null for password-only users") String googleSub
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(), user.getEmail(), user.getPasswordHash(), user.getGoogleSub());
    }
}
