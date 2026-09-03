package com.fantasy.db.user.dto;

import com.fantasy.db.user.UserAvatar;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetAvatarRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "image/png, image/jpeg or image/webp: the type the bytes will be served as")
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^image/(png|jpeg|webp)$", message = "must be image/png, image/jpeg or image/webp")
        String contentType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The encoded image, already scaled to the size the app draws; at most 512 KiB")
        @NotNull
        @Size(min = 1, max = UserAvatar.MAX_BYTES)
        byte[] data
) {}
