package com.fantasy.db.user;

import com.fantasy.db.exception.EmailAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

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
                .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
