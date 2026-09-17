package com.fantasy.db.share;

import java.time.Instant;

/**
 * A shared snapshot together with the owner's current public name and the stamp on their profile
 * picture, null where they have none. Both live on the account rather than in the snapshot, so a
 * rename or a new picture follows onto links that were already shared.
 */
public record SharedProjection(ProjectionShare share, String authorUsername,
                               Instant authorAvatarUpdatedAt) {}
