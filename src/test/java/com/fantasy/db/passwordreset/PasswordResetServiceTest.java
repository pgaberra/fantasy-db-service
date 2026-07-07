package com.fantasy.db.passwordreset;

import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PasswordResetService.class)
class PasswordResetServiceTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Test
    void issuesTokenForPasswordUser() {
        userRepository.save(User.create("amy@example.com", "old-hash"));

        Optional<PasswordResetService.IssuedToken> issued = passwordResetService.issueToken("amy@example.com");

        assertThat(issued).isPresent();
        assertThat(issued.get().token()).isNotBlank();
        assertThat(issued.get().expiresAt()).isNotNull();
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void issuesNoTokenForUnknownEmail() {
        assertThat(passwordResetService.issueToken("nobody@example.com")).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void issuesNoTokenForGoogleOnlyAccount() {
        userRepository.save(User.createWithGoogle("google@example.com", "sub-1"));

        assertThat(passwordResetService.issueToken("google@example.com")).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void reissuingInvalidatesThePreviousToken() {
        userRepository.save(User.create("re@example.com", "old-hash"));
        String firstToken = passwordResetService.issueToken("re@example.com").orElseThrow().token();

        passwordResetService.issueToken("re@example.com");

        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThatThrownBy(() -> passwordResetService.resetPassword(firstToken, "new-hash"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void resetUpdatesPasswordAndConsumesToken() {
        userRepository.save(User.create("reset@example.com", "old-hash"));
        String token = passwordResetService.issueToken("reset@example.com").orElseThrow().token();

        passwordResetService.resetPassword(token, "new-hash");

        User updated = userRepository.findByEmailIgnoreCase("reset@example.com").orElseThrow();
        assertThat(updated.getPasswordHash()).isEqualTo("new-hash");
        // A reset invalidates existing sessions: the token version is bumped (0 -> 1).
        assertThat(updated.getTokenVersion()).isEqualTo(1);
        assertThatThrownBy(() -> passwordResetService.resetPassword(token, "another-hash"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rejectsUnknownToken() {
        assertThatThrownBy(() -> passwordResetService.resetPassword("not-a-real-token", "new-hash"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rejectsExpiredToken() {
        userRepository.save(User.create("expired@example.com", "old-hash"));
        PasswordResetService expiring =
                new PasswordResetService(userRepository, tokenRepository, Duration.ofMinutes(-1));
        String token = expiring.issueToken("expired@example.com").orElseThrow().token();

        assertThatThrownBy(() -> passwordResetService.resetPassword(token, "new-hash"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
