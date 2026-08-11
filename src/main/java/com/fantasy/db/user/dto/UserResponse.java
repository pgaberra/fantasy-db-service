package com.fantasy.db.user.dto;

import com.fantasy.db.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(description = "The account's public name; null until the user picks one") String username,
        @Schema(description = "Null for users who only authenticate via a social provider") String passwordHash,
        @Schema(description = "Google subject id; null otherwise") String googleSub,
        @Schema(description = "Facebook subject id; null otherwise") String facebookSub,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Session-invalidation counter; the BFF stamps it into refresh tokens "
                        + "and rejects a refresh whose value is stale") int tokenVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the account's email address has been verified") boolean emailVerified
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getGoogleSub(),
                user.getFacebookSub(),
                user.getTokenVersion(),
                user.isEmailVerified());
    }
}
