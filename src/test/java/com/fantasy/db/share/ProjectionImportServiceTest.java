package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerBasis;
import com.fantasy.db.projection.PlayerIdSpace;
import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.ScoringType;
import com.fantasy.db.projection.SkaterPosition;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.projection.UserProjectionService;
import com.fantasy.db.projection.dto.EspnSync;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.PositionOverride;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.projection.dto.UpdateProjectionData;
import com.fantasy.db.projection.dto.YahooSync;
import com.fantasy.db.share.dto.SharedPlayer;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ProjectionImportService.class, ProjectionShareService.class, UserProjectionService.class})
class ProjectionImportServiceTest {

    @Autowired
    private ProjectionImportService projectionImportService;

    @Autowired
    private ProjectionShareService projectionShareService;

    @Autowired
    private UserProjectionService userProjectionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectionShareRepository projectionShareRepository;

    @Autowired
    private UserProjectionRepository userProjectionRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID readerId;
    private int authorCount;

    @BeforeEach
    void createReader() {
        readerId = userRepository.save(User.create("reader@example.com", "hash")).getId();
    }

    private static ProjectionData projectionData() {
        return projectionData(List.of(new PositionOverride(2, List.of(SkaterPosition.LW,
                SkaterPosition.RW))));
    }

    private static ProjectionData projectionData(List<PositionOverride> overrides) {
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
                new YahooSync("Beer League", "453.l.12345", Instant.parse("2026-08-01T10:00:00Z")),
                new EspnSync("ESPN League", "123456", Instant.parse("2026-08-01T10:00:00Z")),
                "123456",
                PlayerBasis.LAST_SEASON,
                Instant.parse("2026-08-16T04:00:00Z"), null, null);
        return new ProjectionData(settings, List.of(
                new PlayerProjection(1, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0))),
                new PlayerProjection(2, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 80.0), Map.of("goals", 51.0))),
                new PlayerProjection(3, PlayerType.GOALIE,
                        new PlayerStats(Map.of("gp", 60.0), Map.of("wins", 38.0)))),
                null,
                overrides);
    }

    /** What a share publishes: the whole ranking, which is also what an import copies. */
    private static List<SharedPlayer> publishedRows() {
        return List.of(
                new SharedPlayer(
                        1, "Connor McDavid", "EDM", "https://example.test/mcdavid.png", List.of("C"),
                        PlayerType.SKATER, 1, 412.5,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0))),
                new SharedPlayer(
                        2, "Leon Draisaitl", "EDM", "https://example.test/draisaitl.png", List.of("C"),
                        PlayerType.SKATER, 2, 389.7,
                        new PlayerStats(Map.of("gp", 80.0), Map.of("goals", 51.0))),
                new SharedPlayer(
                        3, "Igor Shesterkin", "NYR", null, null,
                        PlayerType.GOALIE, 3, 301.2,
                        new PlayerStats(Map.of("gp", 60.0), Map.of("wins", 38.0))));
    }

    /**
     * A fresh author per share: a user may keep only one projection of their own, and a projection
     * may be shared only once, so two links mean two accounts — which is what importing two
     * boards means anyway.
     */
    private String share(String name) {
        return share(name, projectionData());
    }

    private String share(String name, ProjectionData data) {
        String handle = "alex" + authorCount++;
        User author = userRepository.save(User.create(handle + "@example.com", "hash"));
        author.updateUsername(handle);
        UUID authorId = userRepository.save(author).getId();
        UserProjection projection = userProjectionService.create(
                authorId, name, ProjectionKind.PROJECTION, null, data, PlayerIdSpace.YAHOO);
        return projectionShareService.share(authorId, projection.getId(), publishedRows()).getToken();
    }

    /**
     * The reader copies the board they read. The stamp goes out on the share page and comes back
     * with the press, so it has to survive the round trip through storage digit for digit.
     */
    @Test
    void copiesWhenTheBoardIsStillTheOneTheReaderSaw() {
        String token = share("My league");
        // Out to storage and back, the way the page's copy of the stamp was read.
        entityManager.flush();
        entityManager.clear();
        Instant seen = projectionShareRepository.findByToken(token).orElseThrow().getUpdatedAt();

        UserProjection imported = projectionImportService.importFrom(readerId, token, null, seen);

        assertThat(imported.getData().players()).hasSize(3);
    }

    /**
     * A link follows its projection, so the author can change the board while someone reads it.
     * Only the latest board is kept, so the copy is refused rather than made of numbers the
     * reader never saw, and nothing lands in their account.
     */
    @Test
    void refusesToCopyABoardThatChangedSinceTheReaderSawIt() {
        String token = share("My league");
        Instant seen = projectionShareRepository.findByToken(token).orElseThrow().getUpdatedAt()
                .minusSeconds(60);
        long before = userProjectionRepository.count();

        assertThatThrownBy(() -> projectionImportService.importFrom(readerId, token, null, seen))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(userProjectionRepository.count()).isEqualTo(before);
    }

    @Test
    void copiesTheBoardTheShareWasPublishedWith() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getUserId()).isEqualTo(readerId);
        assertThat(imported.getKind()).isEqualTo(ProjectionKind.IMPORTED);
        assertThat(imported.getName()).isEqualTo("My league");
        assertThat(imported.getSeason()).isEqualTo(Season.SEASON_2026_2027);
        assertThat(imported.getData().players()).hasSize(3);
        assertThat(imported.getData().settings().statWeights()).containsEntry("goals", 4.5);
    }

    /**
     * The rows come from the snapshot rather than from the author's projection, which has moved on
     * since — and they arrive stripped back to what a projection stores, since the identity and
     * rank a published row carries belong to the page that renders it, not to the importer's copy.
     */
    @Test
    void copiesTheSnapshotRatherThanTheAuthorsProjectionAsItStandsNow() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getData().players())
                .extracting(PlayerProjection::playerId)
                .containsExactly(1, 2, 3);
        PlayerProjection first = imported.getData().players().getFirst();
        assertThat(first.type()).isEqualTo(PlayerType.SKATER);
        assertThat(first.stats().scoring()).containsEntry("goals", 64.0);
        assertThat(first.stats().utility()).containsEntry("gp", 82.0);
    }

    /**
     * A copy is the reader's own board from the moment it is made. A link follows its projection,
     * and the author's editor publishes after every save, so nothing the author does afterwards
     * may reach the copy: not new numbers, not a new league, not a rename, and not deleting their
     * projection, which takes the share down with it.
     */
    @Test
    void nothingTheAuthorDoesAfterwardsReachesTheCopy() {
        User author = userRepository.save(User.create("later@example.com", "hash"));
        author.updateUsername("later");
        UUID authorId = userRepository.save(author).getId();
        UserProjection original = userProjectionService.create(
                authorId, "My league", ProjectionKind.PROJECTION, null, projectionData(), PlayerIdSpace.YAHOO);
        String token = projectionShareService.share(authorId, original.getId(), publishedRows()).getToken();
        UUID copyId = projectionImportService.importFrom(readerId, token, null).getId();

        ProjectionSettings stored = original.getData().settings();
        ProjectionSettings retuned = new ProjectionSettings(
                stored.scoringType(), Map.of("goals", 9.0), stored.activeScoringColumns(),
                stored.activeUtilityColumns(), stored.scaleSettings(), stored.decimalSettings(),
                stored.useDefaultDecimals(), 16, stored.rosterSlots(), stored.minGoalieGames(),
                stored.yahooSync(), stored.espnSync(), stored.lastEspnLeagueId(),
                stored.playerBasis(), stored.playerPoolSyncedAt(),
                stored.unacknowledgedNewPlayerIds(), stored.manualRanking());
        userProjectionService.update(authorId, original.getId(), "Renamed by the author",
                new UpdateProjectionData(retuned, null, null, null));
        projectionShareService.share(authorId, original.getId(), List.of(new SharedPlayer(
                1, "Connor McDavid", "EDM", null, List.of("C"), PlayerType.SKATER, 1, 999.0,
                new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 99.0)))));
        userProjectionService.delete(authorId, original.getId());
        entityManager.flush();
        entityManager.clear();

        UserProjection copy = userProjectionRepository.findById(copyId).orElseThrow();
        assertThat(copy.getName()).isEqualTo("My league");
        assertThat(copy.getData().settings().statWeights()).containsEntry("goals", 4.5);
        assertThat(copy.getData().settings().leagueSize()).isEqualTo(12);
        assertThat(copy.getData().players()).extracting(PlayerProjection::playerId)
                .containsExactly(1, 2, 3);
        assertThat(copy.getData().players().getFirst().stats().scoring()).containsEntry("goals", 64.0);
    }

    @Test
    void inheritsTheSharesPlayerIdSpaceRatherThanTheDefault() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getPlayerIdSpace()).isEqualTo(PlayerIdSpace.YAHOO);
    }

    @Test
    void stampsWhoTheBoardCameFrom() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getOriginShareToken()).isEqualTo(token);
        assertThat(imported.getOriginAuthorUsername()).isEqualTo("alex0");
    }

    @Test
    void carriesNoneOfTheAuthorsLeagueDetails() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getData().settings().yahooSync()).isNull();
        assertThat(imported.getData().settings().espnSync()).isNull();
        assertThat(imported.getData().settings().lastEspnLeagueId()).isNull();
    }

    /**
     * The corrections are part of the board: the ranking on the page was computed against those
     * positions, so a copy that put players back on the read model's would rank differently from
     * what the importer clicked on.
     */
    @Test
    void inheritsThePositionsTheAuthorCorrected() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getData().positionOverrides()).containsExactly(
                new PositionOverride(2, List.of(SkaterPosition.LW, SkaterPosition.RW)));
    }

    /** Links published before shares carried the corrections still import, with none of them. */
    @Test
    void importsAShareThatCarriesNoCorrections() {
        String token = share("Older board", projectionData(null));

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getData().positionOverrides()).isNull();
    }

    @Test
    void startsWithNoDraftSoTheAuthorsPicksAreNotInherited() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, null);

        assertThat(imported.getData().draft()).isNull();
    }

    @Test
    void takesTheNameTheImporterChose() {
        String token = share("My league");

        UserProjection imported = projectionImportService.importFrom(readerId, token, "  Their board  ");

        assertThat(imported.getName()).isEqualTo("Their board");
    }

    @Test
    void holdsMoreThanOneImportedBoardAtATime() {
        String first = share("First league");
        String second = share("Second league");

        projectionImportService.importFrom(readerId, first, null);
        projectionImportService.importFrom(readerId, second, null);

        assertThat(userProjectionService.findAll(readerId)).hasSize(2);
    }

    @Test
    void leavesTheImportersOwnProjectionAlone() {
        UserProjection own = userProjectionService.create(
                readerId, "Mine", ProjectionKind.PROJECTION, null, projectionData(), PlayerIdSpace.YAHOO);

        projectionImportService.importFrom(readerId, share("My league"), null);

        assertThat(userProjectionService.findById(readerId, own.getId()).getKind())
                .isEqualTo(ProjectionKind.PROJECTION);
        assertThat(userProjectionService.findAll(readerId)).hasSize(2);
    }

    @Test
    void refusesASecondImportUnderTheSameName() {
        String first = share("Same name");
        String second = share("Other league");
        projectionImportService.importFrom(readerId, first, "Same name");

        assertThatThrownBy(() -> projectionImportService.importFrom(readerId, second, "Same name"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * The bug the naming rule was written for: a copy landed in the same list as the importer's
     * own boards under a name one of them already had, and the two were told apart only by the
     * smaller line beneath them. Still true, and still fixed — but by numbering the copy rather
     * than by refusing it, since nobody chose the name that clashed.
     */
    @Test
    void numbersAnImportThatWouldTakeTheNameOfTheImportersOwnProjection() {
        userProjectionService.create(readerId, "My Projection 3", ProjectionKind.PROJECTION, null,
                projectionData(), PlayerIdSpace.YAHOO);
        String token = share("My Projection 3");

        UserProjection copy = projectionImportService.importFrom(readerId, token, null);

        assertThat(copy.getName()).isEqualTo("My Projection 3 (2)");
        assertThat(copy.getKind()).isEqualTo(ProjectionKind.IMPORTED);
        assertThat(userProjectionService.findAll(readerId)).hasSize(2);
    }

    /**
     * Opening the same link a third time is the case that made this worth changing: someone who
     * has copied a board twice already gets a third copy, not an error telling them to go and
     * sort the naming out themselves.
     */
    @Test
    void keepsNumberingForEveryFurtherCopyOfTheSameBoard() {
        String token = share("My league");

        projectionImportService.importFrom(readerId, token, null);
        projectionImportService.importFrom(readerId, token, null);
        UserProjection third = projectionImportService.importFrom(readerId, token, null);

        assertThat(third.getName()).isEqualTo("My league (3)");
        assertThat(userProjectionService.findAll(readerId))
                .extracting(UserProjection::getName)
                .containsExactlyInAnyOrder("My league", "My league (2)", "My league (3)");
    }

    /**
     * The suffix has to fit inside the hundred characters a name gets, so a name already at the
     * cap loses its tail rather than the copy being rejected by the column. Same shape as V19,
     * which had to break the ties already in the table.
     */
    @Test
    void trimsANameAtTheCapToMakeRoomForTheNumber() {
        String longName = "x".repeat(100);
        String token = share(longName);
        projectionImportService.importFrom(readerId, token, null);

        UserProjection second = projectionImportService.importFrom(readerId, token, null);

        assertThat(second.getName()).hasSize(100).endsWith(" (2)");
    }

    /**
     * A name the caller typed is theirs to change, so a clash on that one is still reported.
     * Only the name nobody chose is settled quietly.
     */
    @Test
    void stillRefusesANameTheCallerChoseThatIsAlreadyTaken() {
        userProjectionService.create(readerId, "Taken", ProjectionKind.PROJECTION, null,
                projectionData(), PlayerIdSpace.YAHOO);
        String token = share("My league");

        assertThatThrownBy(() -> projectionImportService.importFrom(readerId, token, "Taken"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Naming the copy yourself still works, and still wins over the numbering. */
    @Test
    void acceptsThatImportUnderAFreeName() {
        userProjectionService.create(readerId, "My Projection 3", ProjectionKind.PROJECTION, null,
                projectionData(), PlayerIdSpace.YAHOO);
        String token = share("My Projection 3");

        UserProjection copy = projectionImportService.importFrom(readerId, token, "Erik's board");

        assertThat(copy.getName()).isEqualTo("Erik's board");
        assertThat(copy.getKind()).isEqualTo(ProjectionKind.IMPORTED);
    }

    @Test
    void refusesATokenThatIsNotAShare() {
        assertThatThrownBy(() -> projectionImportService.importFrom(readerId, "n0tAT0k3n", null))
                .isInstanceOf(NoSuchElementException.class);
    }
}
