package com.fantasy.db.share;

import com.fantasy.db.share.dto.CreateShareRequest;
import com.fantasy.db.share.dto.ShareResponse;
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

@Tag(name = "Projection shares", description = "The owner's control over a projection's public link")
@RestController
@RequestMapping("/api/v1/users/{userId}/projections/{projectionId}/share")
public class ProjectionShareController {

    private final ProjectionShareService projectionShareService;

    public ProjectionShareController(ProjectionShareService projectionShareService) {
        this.projectionShareService = projectionShareService;
    }

    @Operation(summary = "Fetch the projection's share, if it has one")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The projection is shared"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user, or it is not shared")
    })
    @GetMapping
    public ShareResponse get(@PathVariable UUID userId, @PathVariable UUID projectionId) {
        return ShareResponse.from(projectionShareService.findByProjection(userId, projectionId));
    }

    @Operation(summary = "Publish or refresh the projection's public snapshot",
            description = "Idempotent: an already-shared projection keeps its token, so links already "
                    + "posted elsewhere stay valid and start showing the refreshed snapshot.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Share created or refreshed"),
        @ApiResponse(responseCode = "400", description = "Validation failed (too many rows, oversized name)"),
        @ApiResponse(responseCode = "409", description = "The owner has not set a username yet"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user")
    })
    @PutMapping
    public ShareResponse share(@PathVariable UUID userId, @PathVariable UUID projectionId,
                               @Valid @RequestBody CreateShareRequest request) {
        return ShareResponse.from(projectionShareService.share(
                userId, projectionId, request.players()));
    }

    @Operation(summary = "Take the projection's public link down",
            description = "Deletes the snapshot outright. Sharing again mints a new token, so the "
                    + "old link stays dead.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Share deleted"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user, or it is not shared")
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshare(@PathVariable UUID userId, @PathVariable UUID projectionId) {
        projectionShareService.unshare(userId, projectionId);
    }
}
