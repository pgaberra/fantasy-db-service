package com.fantasy.db.projection.dto;

import com.fantasy.db.projection.RankingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlayerTypeRanking(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How this player type is ordered: by its projected stats, or by the order the owner put it in.")
        @NotNull RankingMode mode,
        @Schema(description = "The player ids the owner placed by hand, best first. Players of this type that are absent keep their projected order below the placed ones, so an owner who ranked only a top twenty does not have to place the rest. Ignored while mode is `projected`.")
        @Size(max = 2000) List<@NotNull Integer> order
) {}
