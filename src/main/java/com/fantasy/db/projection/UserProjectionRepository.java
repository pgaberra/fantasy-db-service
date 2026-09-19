package com.fantasy.db.projection;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProjectionRepository extends JpaRepository<UserProjection, UUID> {

    List<UserProjection> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserProjection> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The row an update is about to rewrite, locked until the update commits. A follow is written
     * by two parties — its owner saving a draft, and the author's publish mirroring the board into
     * it — and each writes the whole {@code data} document, so without the lock either could put
     * back what the other had just replaced.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProjection p where p.id = :id and p.userId = :userId")
    Optional<UserProjection> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    /**
     * Whether the user already keeps a board under this name. Drafts are excluded because they
     * are a namespace of their own — a draft is named after the board it was started from — and
     * follows because the author names those; see the partial indexes in V19, V25 and V26.
     */
    boolean existsByUserIdAndNameAndKindNotAndOriginShareTokenIsNull(
            UUID userId, String name, ProjectionKind kind);

    /** The same question for a rename, where the row being renamed is not its own conflict. */
    boolean existsByUserIdAndNameAndKindNotAndOriginShareTokenIsNullAndIdNot(
            UUID userId, String name, ProjectionKind kind, UUID id);

    /** The same question inside the drafts' own namespace. */
    boolean existsByUserIdAndNameAndKind(UUID userId, String name, ProjectionKind kind);

    boolean existsByUserIdAndNameAndKindAndIdNot(
            UUID userId, String name, ProjectionKind kind, UUID id);

    /** The drafts started from a board, to unhook when that board is deleted. */
    List<UserProjection> findBySourceProjectionId(UUID sourceProjectionId);

    Optional<UserProjection> findByUserIdAndOriginShareToken(UUID userId, String originShareToken);

    /** Every follow of a link, locked for the mirror that is about to rewrite them. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProjection p where p.originShareToken = :token")
    List<UserProjection> findAllByOriginShareTokenForUpdate(String token);

    List<UserProjection> findAllByPlayerIdSpace(String playerIdSpace);
}
