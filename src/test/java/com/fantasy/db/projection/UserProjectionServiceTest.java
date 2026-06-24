package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(UserProjectionService.class)
class UserProjectionServiceTest {

    @Autowired
    private UserProjectionService userProjectionService;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    private final UUID userId = UUID.randomUUID();

    private static ProjectionData sampleData() {
        ProjectionSettings settings = new ProjectionSettings(
                ScoringType.POINTS,
                Map.of("goals", 4.5, "assists", 3.0),
                List.of("goals", "assists"),
                List.of("gp"),
                Map.of(),
                Map.of("goals", 0, "assists", 0),
                true,
                12,
                null,
                null,
                null);
        PlayerProjection mcDavid = new PlayerProjection(
                1, PlayerType.SKATER, new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)));
        return new ProjectionData(settings, List.of(mcDavid), null);
    }

    @Test
    void createsAndFindsProjection() {
        UserProjection created = userProjectionService.create(userId, "My league", sampleData());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();

        UserProjection found = userProjectionService.findById(userId, created.getId());
        assertThat(found.getName()).isEqualTo("My league");
        assertThat(found.getData().players()).hasSize(1);
        assertThat(found.getData().settings().scoringType()).isEqualTo(ScoringType.POINTS);
        assertThat(userProjectionService.findAll(userId)).hasSize(1);
    }

    @Test
    void stampsTheConfiguredCurrentSeason() {
        UserProjection created = userProjectionService.create(userId, "Seasoned", sampleData());

        assertThat(created.getSeason()).isEqualTo(Season.SEASON_2026_2027);
    }

    @Test
    void enforcesUniqueNamePerUser() {
        userProjectionRepository.saveAndFlush(
                UserProjection.create(userId, "Dynasty", Season.SEASON_2026_2027, sampleData()));

        assertThatThrownBy(() -> userProjectionRepository.saveAndFlush(
                UserProjection.create(userId, "Dynasty", Season.SEASON_2026_2027, sampleData())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameNameForDifferentUsers() {
        userProjectionService.create(userId, "Standard", sampleData());

        UserProjection other = userProjectionService.create(UUID.randomUUID(), "Standard", sampleData());

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void findByIdIsScopedToOwner() {
        UserProjection mine = userProjectionService.create(userId, "Mine", sampleData());

        assertThatThrownBy(() -> userProjectionService.findById(UUID.randomUUID(), mine.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updatesNameAndData() {
        UserProjection created = userProjectionService.create(userId, "Old", sampleData());

        ProjectionData newData = new ProjectionData(
                sampleData().settings(),
                List.of(new PlayerProjection(
                        2, PlayerType.GOALIE, new PlayerStats(Map.of("gp", 60.0), Map.of("w", 40.0)))),
                null);
        UserProjection updated = userProjectionService.update(userId, created.getId(), "New", newData);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getData().players().getFirst().type()).isEqualTo(PlayerType.GOALIE);
    }

    @Test
    void deleteRemovesProjection() {
        UserProjection created = userProjectionService.create(userId, "Temp", sampleData());

        userProjectionService.delete(userId, created.getId());

        assertThat(userProjectionService.findAll(userId)).isEmpty();
    }
}
