package com.fantasy.db.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    public static final int MAX_BYTES = 512 * 1024;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    // The length is for the test database: without one H2 creates a 255-byte column, which no
    // picture fits. Postgres validates the column as bytea and ignores it.
    @Column(nullable = false, length = 2 * MAX_BYTES)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAvatar() {
        // Required by JPA
    }

    private UserAvatar(UUID userId, String contentType, byte[] data, Instant updatedAt) {
        this.userId = userId;
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public static UserAvatar of(UUID userId, String contentType, byte[] data) {
        return new UserAvatar(userId, contentType, data, Instant.now());
    }

    public void replace(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
