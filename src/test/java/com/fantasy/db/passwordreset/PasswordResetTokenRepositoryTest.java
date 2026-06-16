package com.fantasy.db.passwordreset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    void findsByTokenHash() {
        tokenRepository.save(PasswordResetToken.create(
                UUID.randomUUID(), "hash-abc", Instant.now().plusSeconds(1800)));

        assertThat(tokenRepository.findByTokenHash("hash-abc")).isPresent();
        assertThat(tokenRepository.findByTokenHash("no-such-hash")).isEmpty();
    }

    @Test
    void deletesByUserId() {
        UUID userId = UUID.randomUUID();
        tokenRepository.save(PasswordResetToken.create(userId, "hash-1", Instant.now().plusSeconds(1800)));
        tokenRepository.save(PasswordResetToken.create(
                UUID.randomUUID(), "hash-2", Instant.now().plusSeconds(1800)));

        tokenRepository.deleteByUserId(userId);

        assertThat(tokenRepository.findByTokenHash("hash-1")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("hash-2")).isPresent();
    }
}
