package com.fantasy.db.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProjectionRepository extends JpaRepository<UserProjection, UUID> {

    List<UserProjection> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserProjection> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Whether the user already keeps something under this name within one naming namespace —
     * their boards or their drafts, never both at once. A draft is named after the board it was
     * started from, so the two namespaces are kept apart on purpose; see the partial indexes in
     * V19 and V25.
     */
    boolean existsByUserIdAndNameAndKindIn(
            UUID userId, String name, Collection<ProjectionKind> kinds);

    /** The same question for a rename, where the row being renamed is not its own conflict. */
    boolean existsByUserIdAndNameAndKindInAndIdNot(
            UUID userId, String name, Collection<ProjectionKind> kinds, UUID id);

    /** The drafts started from a board, to unhook when that board is deleted. */
    List<UserProjection> findBySourceProjectionId(UUID sourceProjectionId);

    List<UserProjection> findAllByPlayerIdSpace(String playerIdSpace);
}
