package com.fantasy.db.user;

import com.fantasy.db.user.dto.AvatarResponse;
import com.fantasy.db.user.dto.SetAvatarRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User avatars", description = "The account's profile picture")
@RestController
@RequestMapping("/api/v1/users/{userId}/avatar")
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    public UserAvatarController(UserAvatarService userAvatarService) {
        this.userAvatarService = userAvatarService;
    }

    @Operation(summary = "Fetch the account's profile picture")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avatar returned"),
        @ApiResponse(responseCode = "404", description = "The account has no avatar, or there is no such user")
    })
    @GetMapping
    public AvatarResponse getAvatar(@PathVariable UUID userId) {
        return AvatarResponse.from(userAvatarService.find(userId));
    }

    @Operation(summary = "Set the account's profile picture",
            description = "Replaces whatever the account had. The caller is trusted to have scaled the "
                    + "image and checked that it is what its content type says.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avatar set"),
        @ApiResponse(responseCode = "400", description = "Validation failed (content type, or size)"),
        @ApiResponse(responseCode = "404", description = "No such user")
    })
    @PutMapping
    public AvatarResponse setAvatar(@PathVariable UUID userId, @Valid @RequestBody SetAvatarRequest request) {
        return AvatarResponse.from(userAvatarService.set(userId, request.contentType(), request.data()));
    }

    @Operation(summary = "Remove the account's profile picture")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Avatar removed"),
        @ApiResponse(responseCode = "404", description = "The account has no avatar")
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar(@PathVariable UUID userId) {
        userAvatarService.delete(userId);
    }
}
