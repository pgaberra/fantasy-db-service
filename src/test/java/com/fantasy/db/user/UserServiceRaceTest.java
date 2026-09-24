package com.fantasy.db.user;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceRaceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(userRepository);

    @Test
    void aGoogleUserAnotherRequestCreatedConcurrentlyIsNotReportedAsCreated() {
        User theirs = User.createWithGoogle("race@example.com", "google-race");
        when(userRepository.findByGoogleSub("google-race"))
                .thenReturn(Optional.empty(), Optional.of(theirs));
        when(userRepository.findByEmailIgnoreCase("race@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        ResolvedUser resolved = userService.findOrCreateGoogleUser("race@example.com", "google-race");

        assertThat(resolved.user()).isSameAs(theirs);
        assertThat(resolved.created()).isFalse();
    }

    @Test
    void aFacebookUserAnotherRequestCreatedConcurrentlyIsNotReportedAsCreated() {
        User theirs = User.createWithFacebook("fbrace@example.com", "facebook-race");
        when(userRepository.findByFacebookSub("facebook-race"))
                .thenReturn(Optional.empty(), Optional.of(theirs));
        when(userRepository.findByEmailIgnoreCase("fbrace@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        ResolvedUser resolved = userService.findOrCreateFacebookUser("fbrace@example.com", "facebook-race");

        assertThat(resolved.user()).isSameAs(theirs);
        assertThat(resolved.created()).isFalse();
    }
}
