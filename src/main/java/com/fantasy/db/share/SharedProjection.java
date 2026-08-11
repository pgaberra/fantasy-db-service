package com.fantasy.db.share;

/**
 * A shared snapshot together with the owner's current public name. The name lives on the account
 * rather than in the snapshot, so a rename follows onto links that were already shared.
 */
public record SharedProjection(ProjectionShare share, String authorUsername) {}
