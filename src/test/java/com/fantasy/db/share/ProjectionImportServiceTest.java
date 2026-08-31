package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerBasis;
import com.fantasy.db.projection.PlayerIdSpace;
import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.ScoringType;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionService;
import com.fantasy.db.projection.dto.EspnSync;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.projection.dto.YahooSync;
import com.fantasy.db.share.dto.SharedPlayer;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

    private UUID readerId;
    private int authorCount;

    @BeforeEach
    void createReader() {
        readerId = userRepository.save(User.create("reader@example.com", "hash")).getId();
    }

    private static ProjectionData projectionData() {
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
                Instant.parse("2026-08-16T04:00:00Z"));
        return new ProjectionData(settings, List.of(
                new PlayerProjection(1, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0))),
                new PlayerProjection(2, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 80.0), Map.of("goals", 51.0))),
                new PlayerProjection(3, PlayerType.GOALIE,
                        new PlayerStats(Map.of("gp", 60.0), Map.of("wins", 38.0)))),
                null,
                null);
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
        String handle = "alex" + authorCount++;
        User author = userRepository.save(User.create(handle + "@example.com", "hash"));
        author.updateUsername(handle);
        UUID authorId = userRepository.save(author).getId();
        UserProjection projection = userProjectionService.create(
                authorId, name, ProjectionKind.PROJECTION, null, projectionData(), PlayerIdSpace.YAHOO);
        return projectionShareService.share(authorId, projection.getId(), publishedRows()).getToken();
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

    @Test
    void refusesATokenThatIsNotAShare() {
        assertThatThrownBy(() -> projectionImportService.importFrom(readerId, "n0tAT0k3n", null))
                .isInstanceOf(NoSuchElementException.class);
    }
}
