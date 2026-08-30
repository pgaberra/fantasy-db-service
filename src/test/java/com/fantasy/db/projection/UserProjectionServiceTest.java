package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.projection.dto.UpdateProjectionData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
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
                null,
                null,
                null,
                PlayerBasis.LAST_SEASON,
                Instant.parse("2026-08-16T04:00:00Z"));
        PlayerProjection mcDavid = new PlayerProjection(
                1, PlayerType.SKATER, new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)));
        return new ProjectionData(settings, List.of(mcDavid), null);
    }

    private UserProjection create(String name) {
        return userProjectionService.create(
                userId, name, ProjectionKind.PROJECTION, null, sampleData(), PlayerIdSpace.YAHOO);
    }

    @Test
    void createsAndFindsProjection() {
        UserProjection created = create("My league");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();

        UserProjection found = userProjectionService.findById(userId, created.getId());
        assertThat(found.getName()).isEqualTo("My league");
        assertThat(found.getKind()).isEqualTo(ProjectionKind.PROJECTION);
        assertThat(found.getData().players()).hasSize(1);
        assertThat(found.getData().settings().scoringType()).isEqualTo(ScoringType.POINTS);
        assertThat(userProjectionService.findAll(userId)).hasSize(1);
    }

    /**
     * What the rows started from, and when they were last squared with the player pool, has to
     * survive the jsonb round-trip: it is the only thing that says what a player who joins the
     * pool later should be seeded with.
     */
    @Test
    void keepsThePlayerBasisAndPoolStamp() {
        UserProjection created = create("Based");

        ProjectionSettings stored = userProjectionService.findById(userId, created.getId())
                .getData().settings();
        assertThat(stored.playerBasis()).isEqualTo(PlayerBasis.LAST_SEASON);
        assertThat(stored.playerPoolSyncedAt()).isEqualTo(Instant.parse("2026-08-16T04:00:00Z"));
    }

    @Test
    void stampsTheConfiguredCurrentSeason() {
        UserProjection created = create("Seasoned");

        assertThat(created.getSeason()).isEqualTo(Season.SEASON_2026_2027);
    }

    @Test
    void enforcesUniqueNamePerUserAndKind() {
        userProjectionRepository.saveAndFlush(UserProjection.create(
                userId, "Dynasty", ProjectionKind.PROJECTION, null, Season.SEASON_2026_2027, sampleData(),
                PlayerIdSpace.YAHOO));

        assertThatThrownBy(() -> userProjectionRepository.saveAndFlush(UserProjection.create(
                userId, "Dynasty", ProjectionKind.PROJECTION, null, Season.SEASON_2026_2027, sampleData(),
                PlayerIdSpace.YAHOO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * A preset draft is named after its preset, so it must not collide with a user who happened to
     * give their own projection the same name.
     */
    @Test
    void allowsTheSameNameAcrossKinds() {
        create("Last Season's Stats");

        UserProjection preset = userProjectionService.create(
                userId, "Last Season's Stats", ProjectionKind.PRESET_DRAFT, null, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(preset.getId()).isNotNull();
        assertThat(userProjectionService.findAll(userId)).hasSize(2);
    }

    @Test
    void allowsSameNameForDifferentUsers() {
        create("Standard");

        UserProjection other = userProjectionService.create(
                UUID.randomUUID(), "Standard", ProjectionKind.PROJECTION, null, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void rejectsASecondProjectionForTheSameUser() {
        create("First");

        assertThatThrownBy(() -> create("Second"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsASecondDraftAgainstTheSamePreset() {
        userProjectionService.create(userId, "Last Season's Stats", ProjectionKind.PRESET_DRAFT,
                ProjectionPreset.LAST_SEASON, sampleData(), PlayerIdSpace.YAHOO);

        assertThatThrownBy(() -> userProjectionService.create(
                userId, "Last Season's Stats again", ProjectionKind.PRESET_DRAFT,
                ProjectionPreset.LAST_SEASON, sampleData(), PlayerIdSpace.YAHOO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The presets are separate starting points. Drafting against one is no reason to be barred
    // from the other, which is what the rule said while a preset draft was a single thing.
    @Test
    void allowsOneDraftAgainstEachPreset() {
        userProjectionService.create(userId, "Last Season's Stats", ProjectionKind.PRESET_DRAFT,
                ProjectionPreset.LAST_SEASON, sampleData(), PlayerIdSpace.YAHOO);

        UserProjection model = userProjectionService.create(userId, "AI Projection",
                ProjectionKind.PRESET_DRAFT, ProjectionPreset.MODEL, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(model.getPreset()).isEqualTo(ProjectionPreset.MODEL);
        assertThat(userProjectionService.findAll(userId)).hasSize(2);
    }

    // Drafts stored before the column existed carry no preset. Two of those are still one thing
    // too many — the migration gives every one of them a preset, so this is the belt to that brace.
    @Test
    void rejectsASecondPresetDraftWithNoPresetRecorded() {
        userProjectionService.create(userId, "Last Season's Stats", ProjectionKind.PRESET_DRAFT,
                null, sampleData(), PlayerIdSpace.YAHOO);

        assertThatThrownBy(() -> userProjectionService.create(
                userId, "Another preset", ProjectionKind.PRESET_DRAFT, null, sampleData(),
                PlayerIdSpace.YAHOO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByIdIsScopedToOwner() {
        UserProjection mine = create("Mine");

        assertThatThrownBy(() -> userProjectionService.findById(UUID.randomUUID(), mine.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updatesNameAndData() {
        UserProjection created = create("Old");

        UpdateProjectionData newData = new UpdateProjectionData(
                sampleData().settings(),
                List.of(new PlayerProjection(
                        2, PlayerType.GOALIE, new PlayerStats(Map.of("gp", 60.0), Map.of("w", 40.0)))),
                null);
        UserProjection updated = userProjectionService.update(userId, created.getId(), "New", newData);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getData().players().getFirst().type()).isEqualTo(PlayerType.GOALIE);
    }

    /**
     * The point of the partial update: the ~0.5 MB of player rows do not have to be re-sent by an
     * autosave that only moved a stat weight.
     */
    @Test
    void omittedPlayersKeepTheStoredRows() {
        UserProjection created = create("Old");
        ProjectionSettings changedSettings = sampleData().settings();

        UserProjection updated = userProjectionService.update(userId, created.getId(), "New",
                new UpdateProjectionData(changedSettings, null, null));

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getData().players()).isEqualTo(sampleData().players());
    }

    /**
     * This asserted the opposite until an autosave emptied a real projection. The distinction
     * between "omitted" and "empty" cannot survive the trip: the BFF's OpenAPI-generated request
     * model initialises the list to an empty {@code ArrayList}, so a body that leaves players out
     * arrives here as empty. Since a projection covers every player in the league, no caller ever
     * wants none — the safe reading of an empty list is the same as no list at all.
     */
    @Test
    void emptyPlayersKeepTheStoredRowsToo() {
        UserProjection created = create("Old");

        UserProjection updated = userProjectionService.update(userId, created.getId(), "New",
                new UpdateProjectionData(sampleData().settings(), List.of(), null));

        assertThat(updated.getData().players()).isEqualTo(sampleData().players());
    }

    @Test
    void deleteRemovesProjection() {
        UserProjection created = create("Temp");

        userProjectionService.delete(userId, created.getId());

        assertThat(userProjectionService.findAll(userId)).isEmpty();
    }

    @Test
    void stampsTheIdSpaceTheCallerStatedRatherThanAssumingYahoo() {
        UserProjection espn = userProjectionService.create(
                userId, "On ESPN ids", ProjectionKind.PROJECTION, null, sampleData(), PlayerIdSpace.ESPN);

        assertThat(espn.getPlayerIdSpace()).isEqualTo(PlayerIdSpace.ESPN);
    }
}
