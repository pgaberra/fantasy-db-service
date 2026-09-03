package com.fantasy.db.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({UserService.class, UserAvatarService.class})
class UserAvatarServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 4, 5};

    @Autowired
    private UserService userService;

    @Autowired
    private UserAvatarService userAvatarService;

    @Test
    void storesAPictureAndReadsItBack() {
        User user = userService.create("pic@example.com", "hash");

        userAvatarService.set(user.getId(), "image/png", PNG);
        UserAvatar stored = userAvatarService.find(user.getId());

        assertThat(stored.getContentType()).isEqualTo("image/png");
        assertThat(stored.getData()).isEqualTo(PNG);
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void replacesThePictureTheAccountHad() {
        User user = userService.create("replace@example.com", "hash");
        userAvatarService.set(user.getId(), "image/png", PNG);

        userAvatarService.set(user.getId(), "image/jpeg", JPEG);
        UserAvatar stored = userAvatarService.find(user.getId());

        assertThat(stored.getContentType()).isEqualTo("image/jpeg");
        assertThat(stored.getData()).isEqualTo(JPEG);
    }

    @Test
    void storesAPictureOfTheLargestAllowedSize() {
        User user = userService.create("large@example.com", "hash");
        byte[] largest = new byte[UserAvatar.MAX_BYTES];

        userAvatarService.set(user.getId(), "image/png", largest);

        assertThat(userAvatarService.find(user.getId()).getData()).hasSize(UserAvatar.MAX_BYTES);
    }

    @Test
    void refusesAPictureForAnUnknownAccount() {
        assertThatThrownBy(() -> userAvatarService.set(UUID.randomUUID(), "image/png", PNG))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void reportsAnAccountWithoutAPicture() {
        User user = userService.create("none@example.com", "hash");

        assertThatThrownBy(() -> userAvatarService.find(user.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removesThePicture() {
        User user = userService.create("remove@example.com", "hash");
        userAvatarService.set(user.getId(), "image/png", PNG);

        userAvatarService.delete(user.getId());

        assertThatThrownBy(() -> userAvatarService.find(user.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void refusesToRemoveAPictureThatIsNotThere() {
        User user = userService.create("nothing@example.com", "hash");

        assertThatThrownBy(() -> userAvatarService.delete(user.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
