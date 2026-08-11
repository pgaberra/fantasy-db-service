package com.fantasy.db.user;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(UserService.class)
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void createsAndFindsUser() {
        User created = userService.create("dave@example.com", "hashed");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(userService.findByEmail("dave@example.com")).isPresent();
        assertThat(userService.existsByEmail("dave@example.com")).isTrue();
    }

    @Test
    void rejectsDuplicateEmail() {
        userService.create("erin@example.com", "hashed");

        assertThatThrownBy(() -> userService.create("erin@example.com", "other"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createsPasswordLessGoogleUserWhenNoneExists() {
        User user = userService.findOrCreateGoogleUser("gina@example.com", "google-1");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleSub()).isEqualTo("google-1");
    }

    @Test
    void linksGoogleSubToExistingPasswordAccountWithSameEmail() {
        User passwordUser = userService.create("link@example.com", "hashed");

        User linked = userService.findOrCreateGoogleUser("link@example.com", "google-2");

        assertThat(linked.getId()).isEqualTo(passwordUser.getId());
        assertThat(linked.getGoogleSub()).isEqualTo("google-2");
        assertThat(linked.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void returnsTheSameUserForARepeatedGoogleLogin() {
        User first = userService.findOrCreateGoogleUser("repeat@example.com", "google-3");

        User second = userService.findOrCreateGoogleUser("repeat@example.com", "google-3");

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void createsPasswordLessFacebookUserWhenNoneExists() {
        User user = userService.findOrCreateFacebookUser("fern@example.com", "facebook-1");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getFacebookSub()).isEqualTo("facebook-1");
    }

    @Test
    void linksFacebookSubToExistingPasswordAccountWithSameEmail() {
        User passwordUser = userService.create("fblink@example.com", "hashed");

        User linked = userService.findOrCreateFacebookUser("fblink@example.com", "facebook-2");

        assertThat(linked.getId()).isEqualTo(passwordUser.getId());
        assertThat(linked.getFacebookSub()).isEqualTo("facebook-2");
        assertThat(linked.getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void returnsTheSameUserForARepeatedFacebookLogin() {
        User first = userService.findOrCreateFacebookUser("fbrepeat@example.com", "facebook-3");

        User second = userService.findOrCreateFacebookUser("fbrepeat@example.com", "facebook-3");

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void setsTheAccountsPublicName() {
        User user = userService.create("namer@example.com", "hash");

        User named = userService.setUsername(user.getId(), "alex");

        assertThat(named.getUsername()).isEqualTo("alex");
    }

    @Test
    void refusesANameAnotherAccountHolds_whateverTheCase() {
        User first = userService.create("first@example.com", "hash");
        User second = userService.create("second@example.com", "hash");
        userService.setUsername(first.getId(), "alex");

        assertThatThrownBy(() -> userService.setUsername(second.getId(), "ALEX"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void letsAnAccountRestyleItsOwnName() {
        User user = userService.create("restyle@example.com", "hash");
        userService.setUsername(user.getId(), "alex");

        assertThat(userService.setUsername(user.getId(), "Alex").getUsername()).isEqualTo("Alex");
    }

    @Test
    void refusesToNameAnUnknownAccount() {
        assertThatThrownBy(() -> userService.setUsername(UUID.randomUUID(), "ghost"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
