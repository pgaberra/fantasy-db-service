package com.fantasy.db.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProjectionRepository extends JpaRepository<UserProjection, UUID> {

    List<UserProjection> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserProjection> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndKindAndPreset(UUID userId, ProjectionKind kind, ProjectionPreset preset);

    /**
     * Whether the user already keeps something under this name. Preset drafts are excluded
     * because the server names those after their preset and never lists them as the user's own
     * work — see the partial index in V19.
     */
    boolean existsByUserIdAndNameAndKindNot(UUID userId, String name, ProjectionKind kind);

    /** The same question for a rename, where the row being renamed is not its own conflict. */
    boolean existsByUserIdAndNameAndKindNotAndIdNot(
            UUID userId, String name, ProjectionKind kind, UUID id);

    List<UserProjection> findAllByPlayerIdSpace(String playerIdSpace);
}
