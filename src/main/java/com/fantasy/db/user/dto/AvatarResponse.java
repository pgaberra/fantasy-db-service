package com.fantasy.db.user.dto;

import com.fantasy.db.user.UserAvatar;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record AvatarResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) byte[] data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {
    public static AvatarResponse from(UserAvatar avatar) {
        return new AvatarResponse(avatar.getContentType(), avatar.getData(), avatar.getUpdatedAt());
    }
}
