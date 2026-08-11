package com.fantasy.db.share;

import com.fantasy.db.share.dto.SharedProjectionResponse;
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
            description = "Carries no owner identity beyond their public username.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot found"),
        @ApiResponse(responseCode = "404", description = "No share with that token")
    })
    @GetMapping("/{token}")
    public SharedProjectionResponse get(@PathVariable String token) {
        SharedProjection shared = projectionShareService.findByToken(token);
        return SharedProjectionResponse.from(shared.share(), shared.authorUsername());
    }
}
