package com.fantasy.db.emailverification;

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
@Import(EmailVerificationService.class)
class EmailVerificationServiceTest {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Test
    void issuesTokenForUnverifiedUser() {
        userRepository.save(User.create("amy@example.com", "hash"));

        Optional<EmailVerificationService.IssuedToken> issued =
                emailVerificationService.issueToken("amy@example.com");

        assertThat(issued).isPresent();
        assertThat(issued.get().token()).isNotBlank();
        assertThat(issued.get().expiresAt()).isNotNull();
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    void issuesNoTokenForUnknownEmail() {
        assertThat(emailVerificationService.issueToken("nobody@example.com")).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void issuesNoTokenForAlreadyVerifiedAccount() {
        // A Google sign-up is verified by the provider, so there is nothing to verify.
        userRepository.save(User.createWithGoogle("google@example.com", "sub-1"));

        assertThat(emailVerificationService.issueToken("google@example.com")).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    void reissuingInvalidatesThePreviousToken() {
        userRepository.save(User.create("re@example.com", "hash"));
        String firstToken = emailVerificationService.issueToken("re@example.com").orElseThrow().token();

        emailVerificationService.issueToken("re@example.com");

        assertThat(tokenRepository.count()).isEqualTo(1);
        assertThatThrownBy(() -> emailVerificationService.verify(firstToken))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void verifyMarksAccountVerifiedAndConsumesToken() {
        userRepository.save(User.create("verify@example.com", "hash"));
        String token = emailVerificationService.issueToken("verify@example.com").orElseThrow().token();

        emailVerificationService.verify(token);

        User updated = userRepository.findByEmailIgnoreCase("verify@example.com").orElseThrow();
        assertThat(updated.isEmailVerified()).isTrue();
        assertThatThrownBy(() -> emailVerificationService.verify(token))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rejectsUnknownToken() {
        assertThatThrownBy(() -> emailVerificationService.verify("not-a-real-token"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void rejectsExpiredToken() {
        userRepository.save(User.create("expired@example.com", "hash"));
        EmailVerificationService expiring =
                new EmailVerificationService(userRepository, tokenRepository, Duration.ofMinutes(-1));
        String token = expiring.issueToken("expired@example.com").orElseThrow().token();

        assertThatThrownBy(() -> emailVerificationService.verify(token))
                .isInstanceOf(NoSuchElementException.class);
    }
}
