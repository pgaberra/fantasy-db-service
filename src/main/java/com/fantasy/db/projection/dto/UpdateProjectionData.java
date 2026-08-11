package com.fantasy.db.projection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The payload of an update. Unlike {@link ProjectionData} — which is always complete, both when
 * a projection is created and when one is returned — this is a partial: {@code players} may be
 * omitted, and the stored rows are then kept.
 *
 * <p>That single exception exists because the player rows are ~0.5 MB while the rest of a
 * projection is about a kilobyte, and the edits that autosave most often (stat weights, draft
 * picks) do not touch them. Sending them on every autosave made saving depend on an upload that
 * was failing outright for users on a slow connection.
 *
 * <p>{@code settings} and {@code draft} keep the ordinary replace semantics: what you send is
 * what is stored, and an omitted {@code draft} still clears it. Only {@code players} is
 * keep-if-absent, so a partial update can never silently drop something small.
 */
public record UpdateProjectionData(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProjectionSettings settings,
        @Schema(description = "Omit to keep the stored player rows unchanged. Send them only when "
                + "they actually change, e.g. after editing a player's projected stats. An empty "
                + "list means the same as omitting them — a projection covers every player, so "
                + "there is no way to ask for none.")
        @Valid List<PlayerProjection> players,
        @Schema(description = "In-draft state for this projection. Replaced on every update — omit it to clear.")
        @Valid DraftState draft
) {}
