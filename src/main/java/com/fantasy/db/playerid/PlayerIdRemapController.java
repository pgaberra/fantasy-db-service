package com.fantasy.db.playerid;

import com.fantasy.db.playerid.dto.PlayerIdRemapRequest;
import com.fantasy.db.playerid.dto.PlayerIdRemapResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Player ids",
        description = "One-off: rewrite stored player ids from one platform's numbering to another's")
@RestController
@RequestMapping("/api/v1/admin/player-ids")
public class PlayerIdRemapController {

    private final PlayerIdRemapService remapService;

    public PlayerIdRemapController(PlayerIdRemapService remapService) {
        this.remapService = remapService;
    }

    @Operation(summary = "Remap the player ids in saved projections and shares",
            description = "Applies a crosswalk of old id → new id to every projection and share "
                    + "still keyed by Yahoo's ids, and marks them as keyed by ESPN's. Ids the "
                    + "crosswalk does not cover are left as they are, never dropped. Defaults to "
                    + "a dry run: pass dryRun=false to actually write.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Remap reported, and applied unless it was a dry run"),
            @ApiResponse(responseCode = "400", description = "The crosswalk is empty, oversized, or maps one id two ways")
    })
    @PostMapping("/remap")
    public PlayerIdRemapResponse remap(@Valid @RequestBody PlayerIdRemapRequest request) {
        return remapService.remap(request);
    }
}
