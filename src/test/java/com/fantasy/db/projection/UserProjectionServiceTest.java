package com.fantasy.db.projection;

import com.fantasy.db.exception.ProjectionNameExistsException;
import com.fantasy.db.exception.ProjectionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(UserProjectionService.class)
class UserProjectionServiceTest {

    @Autowired
    private UserProjectionService userProjectionService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createsAndFindsProjection() {
        UserProjection created = userProjectionService.create(userId, "My league", "{\"x\":1}");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(userProjectionService.findById(userId, created.getId()).getName()).isEqualTo("My league");
        assertThat(userProjectionService.findAll(userId)).hasSize(1);
    }

    @Test
    void rejectsDuplicateNameForSameUser() {
        userProjectionService.create(userId, "Dynasty", "{}");

        assertThatThrownBy(() -> userProjectionService.create(userId, "Dynasty", "{}"))
                .isInstanceOf(ProjectionNameExistsException.class);
    }

    @Test
    void allowsSameNameForDifferentUsers() {
        userProjectionService.create(userId, "Standard", "{}");

        UserProjection other = userProjectionService.create(UUID.randomUUID(), "Standard", "{}");

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void findByIdIsScopedToOwner() {
        UserProjection mine = userProjectionService.create(userId, "Mine", "{}");

        assertThatThrownBy(() -> userProjectionService.findById(UUID.randomUUID(), mine.getId()))
                .isInstanceOf(ProjectionNotFoundException.class);
    }

    @Test
    void updatesNameAndData() {
        UserProjection created = userProjectionService.create(userId, "Old", "{\"a\":1}");

        UserProjection updated = userProjectionService.update(userId, created.getId(), "New", "{\"a\":2}");

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getData()).isEqualTo("{\"a\":2}");
    }

    @Test
    void deleteRemovesProjection() {
        UserProjection created = userProjectionService.create(userId, "Temp", "{}");

        userProjectionService.delete(userId, created.getId());

        assertThat(userProjectionService.findAll(userId)).isEmpty();
    }
}
