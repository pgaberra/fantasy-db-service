package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.CreateProjectionRequest;
import com.fantasy.db.projection.dto.ProjectionResponse;
import com.fantasy.db.projection.dto.ProjectionSummaryResponse;
import com.fantasy.db.projection.dto.UpdateProjectionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "User projections", description = "Saved player projections, scoped to a user")
@RestController
@RequestMapping("/api/v1/users/{userId}/projections")
public class UserProjectionController {

    private final UserProjectionService userProjectionService;

    public UserProjectionController(UserProjectionService userProjectionService) {
        this.userProjectionService = userProjectionService;
    }

    @Operation(summary = "List a user's saved projections (metadata only, without the projection data)")
    @ApiResponse(responseCode = "200", description = "The user's projections, newest first")
    @GetMapping
    public List<ProjectionSummaryResponse> list(@PathVariable UUID userId) {
        return userProjectionService.findAll(userId).stream()
                .map(ProjectionSummaryResponse::from)
                .toList();
    }

    @Operation(summary = "Fetch a single saved projection, including its data")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projection found"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user")
    })
    @GetMapping("/{id}")
    public ProjectionResponse get(@PathVariable UUID userId, @PathVariable UUID id) {
        return ProjectionResponse.from(userProjectionService.findById(userId, id));
    }

    @Operation(summary = "Save a new projection for a user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Projection created"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank name/data or too large)"),
        @ApiResponse(responseCode = "409", description = "The user already has a projection with that name")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProjectionResponse> create(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateProjectionRequest request) {
        UserProjection projection =
                userProjectionService.create(userId, request.name(), request.data());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectionResponse.from(projection));
    }

    @Operation(summary = "Update an existing projection")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projection updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank name/data or too "
                + "large), or the update would empty a projection that has player rows"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409", description = "The user already has another projection with that name")
    })
    @PutMapping("/{id}")
    public ProjectionResponse update(
            @PathVariable UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectionRequest request) {
        return ProjectionResponse.from(
                userProjectionService.update(userId, id, request.name(), request.data()));
    }

    @Operation(summary = "Delete a saved projection")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Projection deleted"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID userId, @PathVariable UUID id) {
        userProjectionService.delete(userId, id);
    }
}
