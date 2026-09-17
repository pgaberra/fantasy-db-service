package com.fantasy.db.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {

    /**
     * When the account's picture last changed, or empty where it has none. A projection rather
     * than a find: the caller only wants to know whether there is a picture and which version it
     * is, and loading the row would drag half a megabyte of image bytes along to answer that.
     */
    @Query("select a.updatedAt from UserAvatar a where a.userId = :userId")
    Optional<Instant> findUpdatedAtByUserId(UUID userId);
}
