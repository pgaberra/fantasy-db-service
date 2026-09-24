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
    void reportsAGoogleUserAsCreatedOnlyWhenThisCallCreatedIt() {
        ResolvedUser first = userService.findOrCreateGoogleUser("new@example.com", "google-new");
        ResolvedUser again = userService.findOrCreateGoogleUser("new@example.com", "google-new");
        userService.create("linked@example.com", "hashed");
        ResolvedUser linked = userService.findOrCreateGoogleUser("linked@example.com", "google-linked");

        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(linked.created()).isFalse();
    }

    @Test
    void reportsAFacebookUserAsCreatedOnlyWhenThisCallCreatedIt() {
        ResolvedUser first = userService.findOrCreateFacebookUser("fbnew@example.com", "facebook-new");
        ResolvedUser again = userService.findOrCreateFacebookUser("fbnew@example.com", "facebook-new");
        userService.create("fblinked@example.com", "hashed");
        ResolvedUser linked = userService.findOrCreateFacebookUser("fblinked@example.com", "facebook-linked");

        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(linked.created()).isFalse();
    }

    @Test
    void createsPasswordLessGoogleUserWhenNoneExists() {
        User user = userService.findOrCreateGoogleUser("gina@example.com", "google-1").user();

        assertThat(user.getId()).isNotNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleSub()).isEqualTo("google-1");
    }

    @Test
    void linksGoogleSubToExistingVerifiedPasswordAccount_keepingItsPassword() {
        User passwordUser = userService.create("link@example.com", "hashed");
        passwordUser.markEmailVerified();

        User linked = userService.findOrCreateGoogleUser("link@example.com", "google-2").user();

        assertThat(linked.getId()).isEqualTo(passwordUser.getId());
        assertThat(linked.getGoogleSub()).isEqualTo("google-2");
        assertThat(linked.getPasswordHash()).isEqualTo("hashed");
        assertThat(linked.getTokenVersion()).isZero();
    }

    @Test
    void linkingGoogleToAnUnverifiedPasswordAccount_dropsThePasswordAndEndsItsSessions() {
        // Someone registered this address with a password but never proved they own it.
        User preRegistered = userService.create("victim@example.com", "attackers-hash");

        User linked = userService.findOrCreateGoogleUser("victim@example.com", "google-owner").user();

        assertThat(linked.getId()).isEqualTo(preRegistered.getId());
        assertThat(linked.getPasswordHash()).isNull();
        assertThat(linked.getTokenVersion()).isEqualTo(1);
        assertThat(linked.isEmailVerified()).isTrue();
    }

    @Test
    void returnsTheSameUserForARepeatedGoogleLogin() {
        User first = userService.findOrCreateGoogleUser("repeat@example.com", "google-3").user();

        User second = userService.findOrCreateGoogleUser("repeat@example.com", "google-3").user();

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void createsPasswordLessFacebookUserWhenNoneExists() {
        User user = userService.findOrCreateFacebookUser("fern@example.com", "facebook-1").user();

        assertThat(user.getId()).isNotNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getFacebookSub()).isEqualTo("facebook-1");
    }

    @Test
    void linksFacebookSubToExistingVerifiedPasswordAccount_keepingItsPassword() {
        User passwordUser = userService.create("fblink@example.com", "hashed");
        passwordUser.markEmailVerified();

        User linked = userService.findOrCreateFacebookUser("fblink@example.com", "facebook-2").user();

        assertThat(linked.getId()).isEqualTo(passwordUser.getId());
        assertThat(linked.getFacebookSub()).isEqualTo("facebook-2");
        assertThat(linked.getPasswordHash()).isEqualTo("hashed");
        assertThat(linked.getTokenVersion()).isZero();
    }

    @Test
    void linkingFacebookToAnUnverifiedPasswordAccount_dropsThePasswordAndEndsItsSessions() {
        User preRegistered = userService.create("fbvictim@example.com", "attackers-hash");

        User linked = userService.findOrCreateFacebookUser("fbvictim@example.com", "facebook-owner").user();

        assertThat(linked.getId()).isEqualTo(preRegistered.getId());
        assertThat(linked.getPasswordHash()).isNull();
        assertThat(linked.getTokenVersion()).isEqualTo(1);
        assertThat(linked.isEmailVerified()).isTrue();
    }

    @Test
    void returnsTheSameUserForARepeatedFacebookLogin() {
        User first = userService.findOrCreateFacebookUser("fbrepeat@example.com", "facebook-3").user();

        User second = userService.findOrCreateFacebookUser("fbrepeat@example.com", "facebook-3").user();

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

    @Test
    void revokeSessionsBumpsTokenVersion() {
        User user = userService.create("revoke@example.com", "hashed");
        int before = user.getTokenVersion();

        userService.revokeSessions(user.getId());

        assertThat(userService.findById(user.getId()).getTokenVersion()).isEqualTo(before + 1);
    }

    @Test
    void revokeSessionsRejectsUnknownUser() {
        assertThatThrownBy(() -> userService.revokeSessions(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
