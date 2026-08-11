package com.fantasy.db.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_sub")
    private String googleSub;

    @Column(name = "facebook_sub")
    private String facebookSub;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Bumped whenever every existing session for this user must be invalidated (e.g. a password
    // reset). The BFF stamps it into the refresh token and rejects a refresh whose value is stale.
    // The account's public name, shown wherever it publishes something. Null until the user
    // picks one — sharing is what requires it, not signing up. Unique regardless of case, which
    // the database enforces with an index on LOWER(username).
    @Column(unique = true, length = 20)
    private String username;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    // Whether the account's email address has been proven. Social sign-ups are verified by the
    // provider; a password sign-up starts unverified until it consumes an email-verification token.
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    protected User() {
        // Required by JPA
    }

    public User(UUID id, String email, String passwordHash, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public static User create(String email, String passwordHash) {
        return new User(UUID.randomUUID(), email, passwordHash, Instant.now());
    }

    public static User createWithGoogle(String email, String googleSub) {
        User user = new User(UUID.randomUUID(), email, null, Instant.now());
        user.googleSub = googleSub;
        user.emailVerified = true;
        return user;
    }

    public static User createWithFacebook(String email, String facebookSub) {
        User user = new User(UUID.randomUUID(), email, null, Instant.now());
        user.facebookSub = facebookSub;
        user.emailVerified = true;
        return user;
    }

    public void updateUsername(String username) {
        this.username = username;
    }

    public void linkGoogle(String googleSub) {
        this.googleSub = googleSub;
        // The provider has proven ownership of this email, so linking it verifies the account.
        this.emailVerified = true;
    }

    public void linkFacebook(String facebookSub) {
        this.facebookSub = facebookSub;
        this.emailVerified = true;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Invalidate every existing session for this user (the BFF rejects refresh tokens issued before). */
    public void bumpTokenVersion() {
        this.tokenVersion++;
    }

    /** Marks the account's email as proven (a verification token was consumed). */
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getFacebookSub() {
        return facebookSub;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
