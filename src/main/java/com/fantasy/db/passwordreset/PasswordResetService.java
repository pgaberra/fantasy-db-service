package com.fantasy.db.passwordreset;

import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final Duration tokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                @Value("${security.password-reset.token-ttl:PT30M}") Duration tokenTtl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenTtl = tokenTtl;
    }

    /**
     * Issues a single-use reset token for the password account with this email, replacing
     * any outstanding tokens. Returns empty when there is no resettable account (unknown
     * email or a Google-only, password-less account) so the caller can respond identically
     * either way and never reveal whether an account exists.
     */
    @Transactional
    public Optional<IssuedToken> issueToken(String email) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(email);
        if (user.isEmpty() || user.get().getPasswordHash() == null) {
            return Optional.empty();
        }
        UUID userId = user.get().getId();
        tokenRepository.deleteByUserId(userId);
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(tokenTtl);
        tokenRepository.save(PasswordResetToken.create(userId, hash(rawToken), expiresAt));
        return Optional.of(new IssuedToken(rawToken, expiresAt));
    }

    /**
     * Consumes a reset token and sets the account's new password hash. The token is
     * single-use: it is marked used so it cannot be replayed. An unknown, expired, or
     * already-used token surfaces as {@link NoSuchElementException} (→ 404).
     */
    @Transactional
    public void resetPassword(String rawToken, String newPasswordHash) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> new NoSuchElementException("Invalid or expired password reset token"));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Account not found for token"));
        user.updatePassword(newPasswordHash);
        user.bumpTokenVersion();
        token.markUsed(Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
