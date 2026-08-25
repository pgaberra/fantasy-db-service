package com.fantasy.db.share.dto;

import com.fantasy.db.projection.dto.PlayerProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Every player row the projection had when it was shared — what an import copies, as opposed to
 * the ranked teaser rows in {@link SharedProjectionData} that the public page renders. Carries no
 * identity and no ranking: the account importing it has both.
 */
public record SharedBoard(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid List<PlayerProjection> players
) {}
