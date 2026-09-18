package com.fantasy.db.share;

import com.fantasy.db.projection.dto.ProjectionResponse;
import com.fantasy.db.share.dto.ImportProjectionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Projection imports", description = "Copying a shared board into a user's own projections")
@RestController
@RequestMapping("/api/v1/users/{userId}/projections/imports")
public class ProjectionImportController {

    private final ProjectionImportService projectionImportService;

    public ProjectionImportController(ProjectionImportService projectionImportService) {
        this.projectionImportService = projectionImportService;
    }

    @Operation(summary = "Copy a shared projection into the user's own, by its share token",
            description = "Anyone holding the token may copy it. The copy is of the board as it was "
                    + "last published, carries no draft, and is stamped with who shared it. "
                    + "Copying the same board again is allowed: with no `name` given, a taken "
                    + "name is resolved to \"<name> (2)\" rather than refused.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Projection imported"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank or oversized token/name)"),
        @ApiResponse(responseCode = "404", description = "No share with that token"),
        @ApiResponse(responseCode = "409", description = "The `name` given is already taken by "
                + "another projection. Omit it and the server picks a free one."),
        @ApiResponse(responseCode = "412", description = "`seenUpdatedAt` was sent and the board "
                + "has changed since. Nothing was copied; read the share again and retry.")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProjectionResponse> importFromShare(
            @PathVariable UUID userId,
            @Valid @RequestBody ImportProjectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectionResponse.from(
                projectionImportService.importFrom(
                        userId, request.token(), request.name(), request.seenUpdatedAt())));
    }
}
