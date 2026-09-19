package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.CreateProjectionRequest;
import com.fantasy.db.projection.dto.ProjectionResponse;
import com.fantasy.db.projection.dto.ProjectionSummaryResponse;
import com.fantasy.db.projection.dto.RenameProjectionRequest;
import com.fantasy.db.projection.dto.StartDraftRequest;
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

    @Operation(summary = "Save a new projection for a user; a name the user already holds is "
            + "numbered (\"My league (2)\") rather than refused, so the saved name is the one in "
            + "the response")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Projection created"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank name/data or too large)"),
        @ApiResponse(responseCode = "409",
                description = "The user already has a draft against that preset, or a request "
                        + "racing this one took the name")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProjectionResponse> create(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateProjectionRequest request) {
        UserProjection projection = userProjectionService.create(
                userId, request.name(), request.kindOrDefault(), request.preset(), request.data(),
                request.playerIdSpace());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectionResponse.from(projection));
    }

    @Operation(summary = "Start a draft against one of the user's boards",
            description = "Copies the board's player rows and position corrections into a draft "
                    + "of its own, with the setup sent here. The board is not written to and is "
                    + "not read again afterwards, so it can be edited or deleted while the draft "
                    + "is under way, and any number of drafts can be started against it.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Draft created"),
        @ApiResponse(responseCode = "400", description = "Validation failed, or the source is itself a draft"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409", description = "A request racing this one took the name")
    })
    @PostMapping("/{id}/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProjectionResponse> startDraft(
            @PathVariable UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody StartDraftRequest request) {
        UserProjection draft = userProjectionService.startDraft(
                userId, id, request.name(), request.data());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectionResponse.from(draft));
    }

    @Operation(summary = "Rename a projection or a draft",
            description = "Without sending the board with it. A name the user typed is refused "
                    + "where it is taken; one the app derived (`derived: true`) is numbered "
                    + "instead, and is skipped altogether where the user has named the row "
                    + "themselves. The saved name is in the response either way.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Renamed, or left as it was"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank or too long)"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409", description = "Another row of the user's holds that name")
    })
    @PutMapping("/{id}/name")
    public ProjectionSummaryResponse rename(
            @PathVariable UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody RenameProjectionRequest request) {
        UserProjection renamed = request.derivedOrDefault()
                ? userProjectionService.renameDerived(userId, id, request.name())
                : userProjectionService.rename(userId, id, request.name(), false);
        return ProjectionSummaryResponse.from(renamed);
    }

    @Operation(summary = "Update an existing projection")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projection updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank name/data or too large)"),
        @ApiResponse(responseCode = "404", description = "No such projection for this user"),
        @ApiResponse(responseCode = "409", description = "The user already has another projection with that name, or the rows were built from another platform's player pool than the stored projection is keyed by")
    })
    @PutMapping("/{id}")
    public ProjectionResponse update(
            @PathVariable UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectionRequest request) {
        return ProjectionResponse.from(userProjectionService.update(
                userId, id, request.name(), request.data(), request.playerIdSpace()));
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
