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
import com.fantasy.db.projection.dto.DraftPick;
import com.fantasy.db.projection.dto.DraftState;
import com.fantasy.db.projection.dto.DraftTeam;
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

    /** What a share publishes: the whole ranking, which is also what a follow holds. */
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

    /** An author with one shared projection. A projection may be shared only once. */
    private record Author(UUID id, UUID projectionId, String token) {}

    private Author author(String name) {
        return author(name, projectionData());
    }

    private Author author(String name, ProjectionData data) {
        String handle = "alex" + authorCount++;
        User author = userRepository.save(User.create(handle + "@example.com", "hash"));
        author.updateUsername(handle);
        UUID authorId = userRepository.save(author).getId();
        UserProjection projection = userProjectionService.create(
                authorId, name, ProjectionKind.PROJECTION, null, data, PlayerIdSpace.YAHOO);
        String token = projectionShareService
                .share(authorId, projection.getId(), publishedRows()).getToken();
        return new Author(authorId, projection.getId(), token);
    }

    private String share(String name) {
        return author(name).token();
    }

    private UserProjection follow(String token) {
        return projectionImportService.follow(readerId, token, null).projection();
    }

    private UserProjection reload(UUID id) {
        entityManager.flush();
        entityManager.clear();
        return userProjectionRepository.findById(id).orElseThrow();
    }

    @Test
    void followsTheBoardTheShareWasPublishedWith() {
        String token = share("My league");

        UserProjection follow = follow(token);

        assertThat(follow.getUserId()).isEqualTo(readerId);
        assertThat(follow.getKind()).isEqualTo(ProjectionKind.IMPORTED);
        assertThat(follow.isFollow()).isTrue();
        assertThat(follow.getName()).isEqualTo("My league");
        assertThat(follow.getSeason()).isEqualTo(Season.SEASON_2026_2027);
        assertThat(follow.getData().players()).hasSize(3);
        assertThat(follow.getData().settings().statWeights()).containsEntry("goals", 4.5);
    }

    /**
     * The rows come from the snapshot rather than from the author's projection, and arrive
     * stripped back to what a projection stores: the identity and rank a published row carries
     * belong to the page that renders it, not to the board in the follower's account.
     */
    @Test
    void holdsTheSnapshotsRowsStrippedBackToWhatAProjectionStores() {
        UserProjection follow = follow(share("My league"));

        assertThat(follow.getData().players())
                .extracting(PlayerProjection::playerId)
                .containsExactly(1, 2, 3);
        PlayerProjection first = follow.getData().players().getFirst();
        assertThat(first.type()).isEqualTo(PlayerType.SKATER);
        assertThat(first.stats().scoring()).containsEntry("goals", 64.0);
        assertThat(first.stats().utility()).containsEntry("gp", 82.0);
    }

    /**
     * The stamp goes out on the share page and comes back with the press, so it has to survive the
     * round trip through storage digit for digit.
     */
    @Test
    void followsWhenTheBoardIsStillTheOneTheReaderSaw() {
        String token = share("My league");
        entityManager.flush();
        entityManager.clear();
        Instant seen = projectionShareRepository.findByToken(token).orElseThrow().getUpdatedAt();

        UserProjection follow = projectionImportService.follow(readerId, token, seen).projection();

        assertThat(follow.getData().players()).hasSize(3);
    }

    /**
     * A link follows its projection, so the author can change the board while someone reads it.
     * The reader is told rather than quietly handed numbers they never saw, and nothing lands in
     * their account.
     */
    @Test
    void refusesToFollowABoardThatChangedSinceTheReaderSawIt() {
        String token = share("My league");
        Instant seen = projectionShareRepository.findByToken(token).orElseThrow().getUpdatedAt()
                .minusSeconds(60);
        long before = userProjectionRepository.count();

        assertThatThrownBy(() -> projectionImportService.follow(readerId, token, seen))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(userProjectionRepository.count()).isEqualTo(before);
    }

    @Test
    void inheritsTheSharesPlayerIdSpaceRatherThanTheDefault() {
        assertThat(follow(share("My league")).getPlayerIdSpace()).isEqualTo(PlayerIdSpace.YAHOO);
    }

    @Test
    void stampsWhoTheBoardCameFrom() {
        String token = share("My league");

        UserProjection follow = follow(token);

        assertThat(follow.getOriginShareToken()).isEqualTo(token);
        assertThat(follow.getOriginAuthorUsername()).isEqualTo("alex0");
    }

    @Test
    void carriesNoneOfTheAuthorsLeagueDetails() {
        UserProjection follow = follow(share("My league"));

        assertThat(follow.getData().settings().yahooSync()).isNull();
        assertThat(follow.getData().settings().espnSync()).isNull();
        assertThat(follow.getData().settings().lastEspnLeagueId()).isNull();
    }

    /**
     * The corrections are part of the board: the ranking on the page was computed against those
     * positions, so a follow that put players back on the read model's would rank differently
     * from what the reader clicked on.
     */
    @Test
    void inheritsThePositionsTheAuthorCorrected() {
        UserProjection follow = follow(share("My league"));

        assertThat(follow.getData().positionOverrides()).containsExactly(
                new PositionOverride(2, List.of(SkaterPosition.LW, SkaterPosition.RW)));
    }

    /** Links published before shares carried the corrections still work, with none of them. */
    @Test
    void followsAShareThatCarriesNoCorrections() {
        UserProjection follow = follow(share("Older board", projectionData(null)));

        assertThat(follow.getData().positionOverrides()).isNull();
    }

    private String share(String name, ProjectionData data) {
        return author(name, data).token();
    }

    @Test
    void startsWithNoDraftSoTheAuthorsPicksAreNotInherited() {
        assertThat(follow(share("My league")).getData().draft()).isNull();
    }

    /**
     * Whether a draft follows its league is part of the draft, and the draft stays the author's:
     * neither a follow nor a copy of the link may start syncing a league the reader never linked.
     */
    @Test
    void neitherAFollowNorACopyTakesTheAuthorsLeagueSync() {
        ProjectionData board = projectionData();
        String token = share("My league", new ProjectionData(board.settings(), board.players(),
                new DraftState(List.of(new DraftTeam("t1", "Author", true)), List.of("t1"),
                        List.of(new DraftPick(1, "t1")), null, null, true),
                board.positionOverrides()));

        assertThat(follow(token).getData().draft()).isNull();
        assertThat(projectionImportService.copy(readerId, token, null).getData().draft()).isNull();
    }

    /**
     * The button on a link is "follow", and pressing it twice is the same link. A second row
     * mirroring the same board would be a copy of it under the same author-given name, with
     * nothing in the list to tell the two apart.
     */
    @Test
    void followingTheSameLinkAgainReturnsTheFollowAlreadyHeld() {
        String token = share("My league");
        ProjectionImportService.Follow first = projectionImportService.follow(readerId, token, null);

        ProjectionImportService.Follow again = projectionImportService.follow(readerId, token, null);

        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(again.projection().getId()).isEqualTo(first.projection().getId());
        assertThat(userProjectionRepository.count()).isEqualTo(authorCount + 1);
    }

    /** Your own board is already in your account; a follow of it would mirror it into itself. */
    @Test
    void refusesToFollowYourOwnLink() {
        Author author = author("My league");

        assertThatThrownBy(() -> projectionImportService.follow(author.id(), author.token(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void holdsAFollowOfMoreThanOneLinkAtATime() {
        follow(share("First league"));
        follow(share("Second league"));

        assertThat(userProjectionService.findAll(readerId)).hasSize(2);
    }

    /**
     * A follow is named by its author, so it sits outside the names the follower has chosen: the
     * follower cannot rename it, and holding it to their namespace would number a stranger's
     * board after theirs and then have the author's next rename fail.
     */
    @Test
    void takesTheSharesNameEvenWhenTheUserHoldsIt() {
        userProjectionService.create(readerId, "My league", ProjectionKind.PROJECTION, null,
                projectionData(), PlayerIdSpace.YAHOO);

        UserProjection follow = follow(share("My league"));

        assertThat(follow.getName()).isEqualTo("My league");
        assertThat(userProjectionService.findAll(readerId)).hasSize(2);
    }

    @Test
    void leavesTheFollowersOwnProjectionAlone() {
        UserProjection own = userProjectionService.create(
                readerId, "Mine", ProjectionKind.PROJECTION, null, projectionData(), PlayerIdSpace.YAHOO);

        follow(share("My league"));

        assertThat(userProjectionService.findById(readerId, own.getId()).getKind())
                .isEqualTo(ProjectionKind.PROJECTION);
    }

    @Test
    void refusesATokenThatIsNotAShare() {
        assertThatThrownBy(() -> projectionImportService.follow(readerId, "n0tAT0k3n", null))
                .isInstanceOf(NoSuchElementException.class);
    }

    /**
     * The whole point of a follow: the author's next publish is what the follower sees. The
     * author's editor publishes after every save, so this is what a save of theirs does.
     */
    @Test
    void mirrorsTheAuthorsNextPublishIntoTheFollow() {
        Author author = author("My league");
        UUID followId = follow(author.token()).getId();

        republish(author, "Renamed by the author", 9.0, 16);

        UserProjection follow = reload(followId);
        assertThat(follow.getName()).isEqualTo("Renamed by the author");
        assertThat(follow.getData().settings().statWeights()).containsEntry("goals", 9.0);
        assertThat(follow.getData().settings().leagueSize()).isEqualTo(16);
        assertThat(follow.getData().players().getFirst().stats().scoring())
                .containsEntry("goals", 99.0);
    }

    /** The board is the author's; the picks made against it are the follower's. */
    @Test
    void keepsTheFollowersOwnDraftWhenTheAuthorPublishes() {
        Author author = author("My league");
        UUID followId = follow(author.token()).getId();
        DraftState draft = new DraftState(
                List.of(new DraftTeam("t1", "Mine", true)), List.of("t1"),
                List.of(new DraftPick(1, "t1")), null, null, true);
        userProjectionService.update(readerId, followId, "My league",
                new UpdateProjectionData(projectionData().settings(), null, draft, null));

        republish(author, "My league", 9.0, 12);

        UserProjection follow = reload(followId);
        assertThat(follow.getData().draft()).isEqualTo(draft);
        assertThat(follow.getData().players().getFirst().stats().scoring())
                .containsEntry("goals", 99.0);
    }

    /**
     * The editor publishes after every save, including saves that change nothing anyone else can
     * see. A follow is not touched by those: its stamp is what orders the follower's list.
     */
    @Test
    void leavesTheFollowAloneWhenAPublishChangesNothing() {
        Author author = author("My league");
        UUID followId = follow(author.token()).getId();
        Instant stamped = reload(followId).getUpdatedAt();

        projectionShareService.share(author.id(), author.projectionId(), publishedRows());

        assertThat(reload(followId).getUpdatedAt()).isEqualTo(stamped);
    }

    /** Publishing again reaches every account following the link, not just the first. */
    @Test
    void mirrorsIntoEveryFollowOfTheLink() {
        Author author = author("My league");
        UUID mine = follow(author.token()).getId();
        UUID theirs = userRepository.save(User.create("second@example.com", "hash")).getId();
        UUID other = projectionImportService.follow(theirs, author.token(), null).projection().getId();

        republish(author, "Renamed by the author", 9.0, 12);

        assertThat(reload(mine).getName()).isEqualTo("Renamed by the author");
        assertThat(reload(other).getName()).isEqualTo("Renamed by the author");
    }

    private void republish(Author author, String name, double goalWeight, int leagueSize) {
        ProjectionSettings stored = projectionData().settings();
        ProjectionSettings retuned = new ProjectionSettings(
                stored.scoringType(), Map.of("goals", goalWeight), stored.activeScoringColumns(),
                stored.activeUtilityColumns(), stored.scaleSettings(), stored.decimalSettings(),
                stored.useDefaultDecimals(), leagueSize, stored.rosterSlots(), stored.minGoalieGames(),
                stored.yahooSync(), stored.espnSync(), stored.lastEspnLeagueId(),
                stored.playerBasis(), stored.playerPoolSyncedAt(),
                stored.unacknowledgedNewPlayerIds(), stored.manualRanking());
        userProjectionService.update(author.id(), author.projectionId(), name,
                new UpdateProjectionData(retuned, null, null, null));
        projectionShareService.share(author.id(), author.projectionId(), List.of(new SharedPlayer(
                1, "Connor McDavid", "EDM", null, List.of("C"), PlayerType.SKATER, 1, 999.0,
                new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 99.0)))));
    }

    @Test
    void copiesTheBoardUnderAServerChosenName() {
        String token = share("My league");

        UserProjection copy = projectionImportService.copy(readerId, token, null);

        assertThat(copy.getName()).isEqualTo("Copy of My league");
        assertThat(copy.getKind()).isEqualTo(ProjectionKind.PROJECTION);
        assertThat(copy.getData().players()).extracting(PlayerProjection::playerId)
                .containsExactly(1, 2, 3);
        assertThat(copy.getData().positionOverrides()).containsExactly(
                new PositionOverride(2, List.of(SkaterPosition.LW, SkaterPosition.RW)));
        assertThat(copy.getData().draft()).isNull();
        assertThat(copy.getSeason()).isEqualTo(Season.SEASON_2026_2027);
        assertThat(copy.getPlayerIdSpace()).isEqualTo(PlayerIdSpace.YAHOO);
    }

    /** A copy is the reader's own board: no link back, and nothing mirrors into it. */
    @Test
    void theCopyFollowsNothing() {
        Author author = author("My league");
        UUID copyId = projectionImportService.copy(readerId, author.token(), null).getId();

        republish(author, "Renamed by the author", 9.0, 16);

        UserProjection copy = reload(copyId);
        assertThat(copy.getOriginShareToken()).isNull();
        assertThat(copy.isFollow()).isFalse();
        assertThat(copy.getName()).isEqualTo("Copy of My league");
        assertThat(copy.getData().settings().statWeights()).containsEntry("goals", 4.5);
    }

    /** One press does one thing: a copy is a copy, and following is a separate call. */
    @Test
    void copyingALinkDoesNotFollowIt() {
        String token = share("My league");

        projectionImportService.copy(readerId, token, null);

        assertThat(userProjectionRepository.findByUserIdAndOriginShareToken(readerId, token))
                .isEmpty();
        assertThat(userProjectionService.findAll(readerId)).hasSize(1);
    }

    /** A follow the reader already has is neither used nor disturbed by a copy. */
    @Test
    void copyingALinkAlreadyFollowedLeavesTheFollowAlone() {
        String token = share("My league");
        UUID followId = follow(token).getId();

        projectionImportService.copy(readerId, token, null);

        assertThat(userProjectionRepository.findByUserIdAndOriginShareToken(readerId, token))
                .map(UserProjection::getId).contains(followId);
        assertThat(userProjectionService.findAll(readerId)).hasSize(2);
    }

    /** Nobody follows their own board, but they may take a copy of it. */
    @Test
    void copiesYourOwnLinkWithoutFollowingIt() {
        Author author = author("My league");

        UserProjection copy = projectionImportService.copy(author.id(), author.token(), null);

        assertThat(copy.getName()).isEqualTo("Copy of My league");
        assertThat(userProjectionRepository.findByUserIdAndOriginShareToken(
                author.id(), author.token())).isEmpty();
    }

    /** The name is the server's suggestion, so a clash is numbered rather than refused. */
    @Test
    void numbersACopyWhoseNameTheUserAlreadyHolds() {
        String token = share("My league");
        projectionImportService.copy(readerId, token, null);

        UserProjection second = projectionImportService.copy(readerId, token, null);

        assertThat(second.getName()).isEqualTo("Copy of My league (2)");
    }

    /** "Copy of " in front of a name at the cap makes one no column would hold. */
    @Test
    void trimsACopyOfANameAtTheCap() {
        String token = share("x".repeat(100));

        UserProjection copy = projectionImportService.copy(readerId, token, null);

        assertThat(copy.getName()).hasSize(100).startsWith("Copy of xxx");
    }

    @Test
    void refusesToCopyABoardThatChangedSinceTheReaderSawIt() {
        String token = share("My league");
        Instant seen = projectionShareRepository.findByToken(token).orElseThrow().getUpdatedAt()
                .minusSeconds(60);
        long before = userProjectionRepository.count();

        assertThatThrownBy(() -> projectionImportService.copy(readerId, token, seen))
                .isInstanceOf(ConcurrentModificationException.class);
        assertThat(userProjectionRepository.count()).isEqualTo(before);
    }

    @Test
    void refusesToCopyATokenThatIsNotAShare() {
        assertThatThrownBy(() -> projectionImportService.copy(readerId, "n0tAT0k3n", null))
                .isInstanceOf(NoSuchElementException.class);
    }
}
