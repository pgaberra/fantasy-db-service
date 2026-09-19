package com.fantasy.db.share;

import com.fantasy.db.projection.dto.ProjectionResponse;
import com.fantasy.db.share.dto.CopyProjectionRequest;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Projection imports",
        description = "Following a shared board, and copying one into a user's own projections")
@RestController
@RequestMapping("/api/v1/users/{userId}/projections")
public class ProjectionImportController {

    private final ProjectionImportService projectionImportService;

    public ProjectionImportController(ProjectionImportService projectionImportService) {
        this.projectionImportService = projectionImportService;
    }

    @Operation(summary = "Follow a shared board by its token",
            description = "Anyone holding the token may follow it. The follow holds the board as "
                    + "it is published now and is rewritten, name and all, every time the author "
                    + "publishes again; the follower's own draft survives that, and is the only "
                    + "thing they may change on it — an update may send the whole projection and "
                    + "only its `data.draft` is taken. Following is idempotent: a user has at "
                    + "most one follow of a token, and following it again returns that one with "
                    + "200 rather than 201. The follow disappears with the share. For a board of "
                    + "one's own, copy it instead (`/projections/copies`).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The user already followed this link; "
                + "their follow is returned untouched"),
        @ApiResponse(responseCode = "201", description = "Now following the board"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank or oversized "
                + "token), or the link is the caller's own board"),
        @ApiResponse(responseCode = "404", description = "No share with that token"),
        @ApiResponse(responseCode = "409", description = "A request racing this one created the "
                + "follow; read the user's projections again"),
        @ApiResponse(responseCode = "412", description = "`seenUpdatedAt` was sent and the board "
                + "has changed since. Nothing was written; read the share again and retry.")
    })
    @PostMapping("/imports")
    public ResponseEntity<ProjectionResponse> follow(
            @PathVariable UUID userId,
            @Valid @RequestBody ImportProjectionRequest request) {
        ProjectionImportService.Follow follow =
                projectionImportService.follow(userId, request.token(), request.seenUpdatedAt());
        return ResponseEntity.status(follow.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ProjectionResponse.from(follow.projection()));
    }

    @Operation(summary = "Copy a shared board into the user's own projections",
            description = "The copy is the board as it is published now, named "
                    + "\"Copy of <the share's name>\" (numbered where the user holds that name "
                    + "already), with no draft and no link back to the share: it is theirs to "
                    + "edit, and nothing the author publishes afterwards reaches it. The user is "
                    + "left following the link as well, unless the link is their own board.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Copy created"),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank or oversized token)"),
        @ApiResponse(responseCode = "404", description = "No share with that token"),
        @ApiResponse(responseCode = "409", description = "A request racing this one took the name "
                + "or created the follow"),
        @ApiResponse(responseCode = "412", description = "`seenUpdatedAt` was sent and the board "
                + "has changed since. Nothing was written; read the share again and retry.")
    })
    @PostMapping("/copies")
    public ResponseEntity<ProjectionResponse> copy(
            @PathVariable UUID userId,
            @Valid @RequestBody CopyProjectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectionResponse.from(
                projectionImportService.copy(userId, request.token(), request.seenUpdatedAt())));
    }
}
