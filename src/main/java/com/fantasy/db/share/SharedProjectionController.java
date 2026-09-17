package com.fantasy.db.share;

import com.fantasy.db.share.dto.SharedProjectionResponse;
import com.fantasy.db.user.dto.AvatarResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shared projections", description = "Snapshot lookup by share token, for the BFF's public page")
@RestController
@RequestMapping("/api/v1/shares")
public class SharedProjectionController {

    private final ProjectionShareService projectionShareService;

    public SharedProjectionController(ProjectionShareService projectionShareService) {
        this.projectionShareService = projectionShareService;
    }

    @Operation(summary = "Fetch a shared snapshot by its token",
            description = "Carries no owner identity beyond their public username and the stamp on their profile picture.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot found"),
        @ApiResponse(responseCode = "404", description = "No share with that token")
    })
    @GetMapping("/{token}")
    public SharedProjectionResponse get(@PathVariable String token) {
        return SharedProjectionResponse.from(projectionShareService.findByToken(token));
    }

    @Operation(summary = "Fetch the profile picture of the account behind a share token",
            description = "Looked up by token, so the caller serving the public page never learns "
                    + "whose account it is. `authorAvatarUpdatedAt` on the snapshot says whether "
                    + "there is one to ask for.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Picture returned"),
        @ApiResponse(responseCode = "404", description = "No share with that token, or its author has no picture")
    })
    @GetMapping("/{token}/avatar")
    public AvatarResponse getAuthorAvatar(@PathVariable String token) {
        return AvatarResponse.from(projectionShareService.findAuthorAvatar(token));
    }
}
