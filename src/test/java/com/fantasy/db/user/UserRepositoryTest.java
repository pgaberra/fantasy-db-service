package com.fantasy.db.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsUserByEmailCaseInsensitively() {
        userRepository.save(User.create("Alice@Example.com", "hash"));

        assertThat(userRepository.findByEmailIgnoreCase("alice@example.com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("ALICE@EXAMPLE.COM")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("bob@example.com")).isEmpty();
    }

    @Test
    void existsByEmailIsCaseInsensitive() {
        userRepository.save(User.create("carol@example.com", "hash"));

        assertThat(userRepository.existsByEmailIgnoreCase("CAROL@example.com")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    void findsUserByGoogleSub() {
        userRepository.save(User.createWithGoogle("greg@example.com", "google-xyz"));

        assertThat(userRepository.findByGoogleSub("google-xyz")).isPresent();
        assertThat(userRepository.findByGoogleSub("no-such-sub")).isEmpty();
    }
}
