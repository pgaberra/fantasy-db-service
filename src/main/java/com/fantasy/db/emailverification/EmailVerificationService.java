package com.fantasy.db.emailverification;

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
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final Duration tokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    @Value("${security.email-verification.token-ttl:P1D}") Duration tokenTtl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenTtl = tokenTtl;
    }

    /**
     * Issues a single-use verification token for the account with this email, replacing any
     * outstanding tokens. Returns empty when there is nothing to verify (unknown email or an
     * already-verified account) so the caller can respond identically either way and never
     * reveal whether an account exists.
     */
    @Transactional
    public Optional<IssuedToken> issueToken(String email) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(email);
        if (user.isEmpty() || user.get().isEmailVerified()) {
            return Optional.empty();
        }
        UUID userId = user.get().getId();
        tokenRepository.deleteByUserId(userId);
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(tokenTtl);
        tokenRepository.save(EmailVerificationToken.create(userId, hash(rawToken), expiresAt));
        return Optional.of(new IssuedToken(rawToken, expiresAt));
    }

    /**
     * Consumes a verification token and marks the account's email as verified. The token is
     * single-use: it is marked used so it cannot be replayed. An unknown, expired, or already-used
     * token surfaces as {@link NoSuchElementException} (→ 404).
     */
    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> new NoSuchElementException("Invalid or expired verification token"));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new NoSuchElementException("Account not found for token"));
        user.markEmailVerified();
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
