package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.PositionOverride;
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
                Instant.parse("2026-08-16T04:00:00Z"),
                List.of(1),
                null);
        PlayerProjection mcDavid = new PlayerProjection(
                1, PlayerType.SKATER, new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)));
        return new ProjectionData(settings, List.of(mcDavid), null, null);
    }

    private UserProjection create(String name) {
        return userProjectionService.create(
                userId, name, ProjectionKind.PROJECTION, null, sampleData(), PlayerIdSpace.YAHOO);
    }

    private UserProjection rename(UserProjection projection, String name) {
        return userProjectionService.update(userId, projection.getId(), name,
                new UpdateProjectionData(sampleData().settings(), null, null, null));
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
     * pool later should be seeded with. So do the players that squaring added and the owner has
     * not acknowledged, or the notice would go with the next page load.
     */
    @Test
    void keepsThePlayerBasisPoolStampAndUnacknowledgedPlayers() {
        UserProjection created = create("Based");

        ProjectionSettings stored = userProjectionService.findById(userId, created.getId())
                .getData().settings();
        assertThat(stored.playerBasis()).isEqualTo(PlayerBasis.LAST_SEASON);
        assertThat(stored.playerPoolSyncedAt()).isEqualTo(Instant.parse("2026-08-16T04:00:00Z"));
        assertThat(stored.unacknowledgedNewPlayerIds()).containsExactly(1);
    }

    @Test
    void stampsTheConfiguredCurrentSeason() {
        UserProjection created = create("Seasoned");

        assertThat(created.getSeason()).isEqualTo(Season.SEASON_2026_2027);
    }

    /**
     * A create settles a taken name rather than refusing it: the work is already done by the time
     * it is saved, and a name the owner can change afterwards is a smaller thing than losing it.
     * The numbering is the one {@code freeNameFrom} does for a share import, so a spreadsheet
     * board and a copied one come out named alike.
     */
    @Test
    void numbersANameTheUserAlreadyHolds() {
        create("Dynasty");

        UserProjection second = create("Dynasty");

        assertThat(second.getName()).isEqualTo("Dynasty (2)");
        assertThat(create("Dynasty").getName()).isEqualTo("Dynasty (3)");
    }

    @Test
    void keepsTheNameTheCallerAskedForWhenItIsFree() {
        UserProjection created = create("Dynasty");

        assertThat(created.getName()).isEqualTo("Dynasty");
    }

    /**
     * The two kinds a user names share one namespace. They are listed together and read by name,
     * so a projection and an imported board under the same name are told apart only by the
     * smaller line beneath them — which is why the second one is numbered.
     */
    @Test
    void numbersAcrossOwnProjectionsAndImportedBoards() {
        create("Erik's board");

        UserProjection imported = userProjectionService.create(
                userId, "Erik's board", ProjectionKind.IMPORTED, null, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(imported.getName()).isEqualTo("Erik's board (2)");
    }

    /** The suffix has to fit the hundred characters a name gets, so the name is trimmed to make room. */
    @Test
    void trimsANameAtTheCapToFitTheNumber() {
        String atTheCap = "N".repeat(100);
        create(atTheCap);

        UserProjection second = create(atTheCap);

        assertThat(second.getName()).hasSize(100).endsWith(" (2)");
    }

    /**
     * A rename is refused where a create is numbered: there the name is the whole of what was
     * asked for, and the page that asked can say so.
     */
    @Test
    void enforcesUniqueNameWhenAProjectionIsRenamed() {
        create("First");
        UserProjection second = create("Second");

        assertThatThrownBy(() -> rename(second, "First"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Its own name is not a conflict, or nothing could be saved twice. */
    @Test
    void letsAProjectionKeepItsOwnNameOnUpdate() {
        UserProjection projection = create("Steady");

        UserProjection saved = rename(projection, "Steady");

        assertThat(saved.getName()).isEqualTo("Steady");
    }

    /**
     * A preset draft is named by the server after its preset and listed as nobody's own work, so
     * it is outside the namespace in both directions: it may not block a user's name, and saving
     * its picks may not be refused because the user took one.
     */
    @Test
    void letsAPresetDraftKeepItsNameBesideAProjectionUsingIt() {
        userProjectionService.create(userId, "AI Projection", ProjectionKind.DRAFT,
                ProjectionPreset.MODEL, sampleData(), PlayerIdSpace.YAHOO);

        UserProjection own = create("AI Projection");

        assertThat(own.getName()).isEqualTo("AI Projection");
        assertThat(userProjectionService.findAll(userId)).hasSize(2);
    }

    /**
     * A preset draft is named after its preset, so it must not collide with a user who happened to
     * give their own projection the same name.
     */
    @Test
    void allowsTheSameNameAcrossKinds() {
        create("Last Season's Stats");

        UserProjection preset = userProjectionService.create(
                userId, "Last Season's Stats", ProjectionKind.DRAFT, null, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(preset.getName()).isEqualTo("Last Season's Stats");
        assertThat(userProjectionService.findAll(userId)).hasSize(2);
    }

    @Test
    void allowsSameNameForDifferentUsers() {
        create("Standard");

        UserProjection other = userProjectionService.create(
                UUID.randomUUID(), "Standard", ProjectionKind.PROJECTION, null, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(other.getName()).isEqualTo("Standard");
    }

    /**
     * A user may keep as many projections as they like — one started from a copy of another is
     * the point of allowing it. What still tells two apart is the name, which
     * {@link #numbersANameTheUserAlreadyHolds()} covers.
     */
    @Test
    void allowsASecondProjectionForTheSameUser() {
        create("First");

        UserProjection second = create("Second");

        assertThat(second.getId()).isNotNull();
        assertThat(userProjectionService.findAll(userId)).hasSize(2);
    }

    /**
     * The limit this feature removed. A draft used to be a field on the board it was drafted
     * against, so a second one had nowhere to go; a preset draft was the same thing said with a
     * unique index. Drafting the same starting point twice is an ordinary thing to want - the
     * second draft is a second row, and the name is what tells them apart.
     */
    @Test
    void allowsASecondDraftAgainstTheSamePreset() {
        userProjectionService.create(userId, "Last Season's Stats", ProjectionKind.DRAFT,
                ProjectionPreset.LAST_SEASON, sampleData(), PlayerIdSpace.YAHOO);

        UserProjection second = userProjectionService.create(
                userId, "Last Season's Stats", ProjectionKind.DRAFT,
                ProjectionPreset.LAST_SEASON, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(second.getName()).isEqualTo("Last Season's Stats (2)");
        assertThat(userProjectionService.findAll(userId)).hasSize(2);
    }

    // The presets are separate starting points. Drafting against one is no reason to be barred
    // from the other, which is what the rule said while a preset draft was a single thing.
    @Test
    void allowsOneDraftAgainstEachPreset() {
        userProjectionService.create(userId, "Last Season's Stats", ProjectionKind.DRAFT,
                ProjectionPreset.LAST_SEASON, sampleData(), PlayerIdSpace.YAHOO);

        UserProjection model = userProjectionService.create(userId, "AI Projection",
                ProjectionKind.DRAFT, ProjectionPreset.MODEL, sampleData(), PlayerIdSpace.YAHOO);

        assertThat(model.getPreset()).isEqualTo(ProjectionPreset.MODEL);
        assertThat(userProjectionService.findAll(userId)).hasSize(2);
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
                null,
                null);
        UserProjection updated = userProjectionService.update(userId, created.getId(), "New", newData);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getData().players().getFirst().type()).isEqualTo(PlayerType.GOALIE);
    }

    /**
     * Rows built from another platform's pool than the stored ones would replace the user's
     * players with whoever that pool gives those numbers to. That is what a save does in the
     * window between switching the pool and migrating the rows, so it is refused whole.
     */
    @Test
    void refusesAnUpdateBuiltFromAnotherPlatformsPool() {
        UserProjection created = create("Old");
        UpdateProjectionData fromEspn = new UpdateProjectionData(sampleData().settings(),
                List.of(new PlayerProjection(
                        3895074, PlayerType.SKATER, new PlayerStats(Map.of("gp", 82.0), Map.of()))),
                null, null);

        assertThatThrownBy(() -> userProjectionService.update(
                userId, created.getId(), "New", fromEspn, PlayerIdSpace.ESPN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nothing was written");

        UserProjection stored = userProjectionService.findById(userId, created.getId());
        assertThat(stored.getName()).isEqualTo("Old");
        assertThat(stored.getData().players().getFirst().playerId()).isEqualTo(1);
    }

    @Test
    void acceptsAnUpdateFromThePoolTheRowsAreKeyedBy() {
        UserProjection created = create("Old");

        UserProjection updated = userProjectionService.update(userId, created.getId(), "New",
                new UpdateProjectionData(sampleData().settings(), null, null, null), PlayerIdSpace.YAHOO);

        assertThat(updated.getName()).isEqualTo("New");
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
                new UpdateProjectionData(changedSettings, null, null, null));

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
                new UpdateProjectionData(sampleData().settings(), List.of(), null, null));

        assertThat(updated.getData().players()).isEqualTo(sampleData().players());
    }

    @Test
    void storesPositionOverrides() {
        UserProjection created = create("Old");

        UserProjection updated = userProjectionService.update(userId, created.getId(), "Old",
                new UpdateProjectionData(sampleData().settings(), null, null,
                        List.of(new PositionOverride(1, List.of(SkaterPosition.LW, SkaterPosition.RW)))));

        assertThat(updated.getData().positionOverrides())
                .containsExactly(new PositionOverride(1, List.of(SkaterPosition.LW, SkaterPosition.RW)));
    }

    /**
     * Resetting every player back to the positions the read model reports is an update that sends
     * no overrides, so an empty list has to clear the stored ones rather than be read as "leave
     * them alone" the way an empty player list is.
     */
    @Test
    void emptyPositionOverridesClearTheStoredOnes() {
        UserProjection created = create("Old");
        userProjectionService.update(userId, created.getId(), "Old",
                new UpdateProjectionData(sampleData().settings(), null, null,
                        List.of(new PositionOverride(1, List.of(SkaterPosition.D)))));

        UserProjection reset = userProjectionService.update(userId, created.getId(), "Old",
                new UpdateProjectionData(sampleData().settings(), null, null, List.of()));

        assertThat(reset.getData().positionOverrides()).isEmpty();
    }

    /**
     * Draft mode and the clear-draft path both save settings and nothing else. Reading that as
     * "no overrides" would delete work neither of them knows exists.
     */
    @Test
    void omittedPositionOverridesKeepTheStoredOnes() {
        UserProjection created = create("Old");
        userProjectionService.update(userId, created.getId(), "Old",
                new UpdateProjectionData(sampleData().settings(), null, null,
                        List.of(new PositionOverride(1, List.of(SkaterPosition.D)))));

        UserProjection updated = userProjectionService.update(userId, created.getId(), "Old",
                new UpdateProjectionData(sampleData().settings(), null, null, null));

        assertThat(updated.getData().positionOverrides())
                .containsExactly(new PositionOverride(1, List.of(SkaterPosition.D)));
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
    /**
     * A draft is a copy of the board it was started against, so the board can be edited - or
     * deleted - without moving the numbers a draft is being picked from.
     */
    @Test
    void startsADraftAsACopyOfTheBoard() {
        UserProjection board = create("My league");

        UserProjection draft = userProjectionService.startDraft(
                userId, board.getId(), null, setupOnly());

        assertThat(draft.getKind()).isEqualTo(ProjectionKind.DRAFT);
        assertThat(draft.getName()).isEqualTo("My league");
        assertThat(draft.getSourceProjectionId()).isEqualTo(board.getId());
        assertThat(draft.getData().players()).isEqualTo(board.getData().players());
        assertThat(draft.isAutoNamed()).isTrue();
    }

    /** The whole of the complaint: one board, as many drafts as the user wants. */
    @Test
    void startsAsManyDraftsAgainstOneBoardAsAsked() {
        UserProjection board = create("My league");

        userProjectionService.startDraft(userId, board.getId(), null, setupOnly());
        UserProjection second = userProjectionService.startDraft(userId, board.getId(), null, setupOnly());
        UserProjection third = userProjectionService.startDraft(userId, board.getId(), null, setupOnly());

        assertThat(second.getName()).isEqualTo("My league (2)");
        assertThat(third.getName()).isEqualTo("My league (3)");
        assertThat(userProjectionService.findAll(userId)).hasSize(4);
    }

    /**
     * The two namespaces. A draft is named after the board it was started from, so it has to be
     * able to hold that name while the board still does - otherwise every first draft would be
     * created as "(2)".
     */
    @Test
    void letsADraftKeepTheNameOfTheBoardItWasStartedFrom() {
        UserProjection board = create("My league");

        UserProjection draft = userProjectionService.startDraft(
                userId, board.getId(), null, setupOnly());

        assertThat(draft.getName()).isEqualTo(board.getName());
    }

    @Test
    void refusesToDraftAgainstADraft() {
        UserProjection board = create("My league");
        UserProjection draft = userProjectionService.startDraft(userId, board.getId(), null, setupOnly());

        assertThatThrownBy(() -> userProjectionService.startDraft(
                userId, draft.getId(), null, setupOnly()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Someone else's board is not there to be drafted against, and says so as a 404. */
    @Test
    void refusesToStartADraftAgainstAnotherUsersBoard() {
        UserProjection board = userProjectionService.create(UUID.randomUUID(), "Theirs",
                ProjectionKind.PROJECTION, null, sampleData(), PlayerIdSpace.YAHOO);

        assertThatThrownBy(() -> userProjectionService.startDraft(
                userId, board.getId(), null, setupOnly()))
                .isInstanceOf(NoSuchElementException.class);
    }

    /** Deleting a board leaves a draft against it standing, holding its own copy of the numbers. */
    @Test
    void keepsADraftWhenTheBoardItCameFromIsDeleted() {
        UserProjection board = create("My league");
        UserProjection draft = userProjectionService.startDraft(userId, board.getId(), null, setupOnly());

        userProjectionService.delete(userId, board.getId());

        UserProjection kept = userProjectionService.findById(userId, draft.getId());
        assertThat(kept.getSourceProjectionId()).isNull();
        assertThat(kept.getData().players()).hasSize(1);
    }

    @Test
    void renameSetsTheNameAndMarksItTheUsersOwn() {
        UserProjection draft = draft("My league");

        UserProjection renamed = userProjectionService.rename(userId, draft.getId(), "Mock #3", false);

        assertThat(renamed.getName()).isEqualTo("Mock #3");
        assertThat(renamed.isAutoNamed()).isFalse();
    }

    /** A name the user typed is the whole of what was asked for, so a clash is reported. */
    @Test
    void renameRefusesANameAnotherDraftHolds() {
        draft("Taken");
        UserProjection other = draft("Mine");

        assertThatThrownBy(() -> userProjectionService.rename(userId, other.getId(), "Taken", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** A draft and a board may hold the same name: they are listed apart and named after it. */
    @Test
    void renameLetsADraftTakeTheNameOfABoard() {
        create("My league");
        UserProjection draft = draft("Something else");

        UserProjection renamed = userProjectionService.rename(userId, draft.getId(), "My league", false);

        assertThat(renamed.getName()).isEqualTo("My league");
    }

    /** What a league sync does: the draft takes the league's name, numbered where it is taken. */
    @Test
    void derivedRenameNumbersANameAnotherDraftHolds() {
        draft("Beer League");
        UserProjection synced = draft("My league");

        UserProjection renamed = userProjectionService.renameDerived(userId, synced.getId(), "Beer League");

        assertThat(renamed.getName()).isEqualTo("Beer League (2)");
        assertThat(renamed.isAutoNamed()).isTrue();
    }

    /**
     * A name its owner typed stands: a sync that overwrote it would destroy the more deliberate
     * of the two names.
     */
    @Test
    void derivedRenameLeavesANameTheUserTypedAlone() {
        UserProjection draft = draft("My league");
        userProjectionService.rename(userId, draft.getId(), "Mock #3", false);

        UserProjection unchanged = userProjectionService.renameDerived(userId, draft.getId(), "Beer League");

        assertThat(unchanged.getName()).isEqualTo("Mock #3");
    }

    private UserProjection draft(String name) {
        return userProjectionService.create(
                userId, name, ProjectionKind.DRAFT, null, sampleData(), PlayerIdSpace.YAHOO);
    }

    /** What the draft page sends: its own settings and setup, with the rows left to the server. */
    private static ProjectionData setupOnly() {
        return new ProjectionData(sampleData().settings(), List.of(), null, null);
    }
}
