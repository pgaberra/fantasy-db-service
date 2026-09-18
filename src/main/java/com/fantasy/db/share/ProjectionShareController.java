package com.fantasy.db.share;

import com.fantasy.db.share.dto.CreateShareRequest;
import com.fantasy.db.share.dto.ShareResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Operation(summary = "Publish or refresh the projection's public board",
            description = "An already-shared projection keeps its token and has its board replaced, "
                    + "so links already posted elsewhere stay valid and show the projection as it "
                    + "was last published.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Share created, or the existing one refreshed"),
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

}
