package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerBasis;
import com.fantasy.db.projection.PlayerIdSpace;
import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.RankingMode;
import com.fantasy.db.projection.ScoringType;
import com.fantasy.db.projection.SkaterPosition;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionService;
import com.fantasy.db.user.User;
import com.fantasy.db.user.UserRepository;
import com.fantasy.db.projection.dto.EspnSync;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.PositionOverride;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.projection.dto.ManualRanking;
import com.fantasy.db.projection.dto.PlayerTypeRanking;
import com.fantasy.db.projection.dto.YahooSync;
import com.fantasy.db.share.dto.SharedPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ProjectionShareService.class, UserProjectionService.class})
class ProjectionShareServiceTest {

    @Autowired
    private ProjectionShareService projectionShareService;

    @Autowired
    private ProjectionShareRepository projectionShareRepository;

    @Autowired
    private UserProjectionService userProjectionService;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void createOwner() {
        User owner = userRepository.save(User.create("owner@example.com", "hash"));
        owner.updateUsername("alex");
        userId = userRepository.save(owner).getId();
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
                new YahooSync("Alexander's Beer League", "453.l.12345", Instant.parse("2026-08-01T10:00:00Z")),
                new EspnSync("Alexander's ESPN League", "123456", Instant.parse("2026-08-01T10:00:00Z")),
                "123456",
                PlayerBasis.LAST_SEASON,
                Instant.parse("2026-08-16T04:00:00Z"),
                List.of(1),
                new ManualRanking(
                        new PlayerTypeRanking(RankingMode.PROJECTED, null),
                        new PlayerTypeRanking(RankingMode.MANUAL, List.of(7, 3))));
        PlayerProjection mcDavid = new PlayerProjection(
                1, PlayerType.SKATER, new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)));
        return new ProjectionData(settings, List.of(mcDavid), null,
                List.of(new PositionOverride(1, List.of(SkaterPosition.C, SkaterPosition.LW))));
    }

    private static List<SharedPlayer> sharedPlayers(String topName) {
        return List.of(new SharedPlayer(
                1,
                topName,
                "EDM",
                "https://example.test/headshot.png",
                List.of("C"),
                PlayerType.SKATER,
                1,
                412.5,
                new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0))));
    }

    private UserProjection projection() {
        return userProjectionService.create(
                userId, "My league", ProjectionKind.PROJECTION, null, projectionData(), PlayerIdSpace.YAHOO);
    }

    @Test
    void sharesAProjectionUnderAnUnguessableToken() {
        UserProjection projection = projection();

        ProjectionShare share = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Connor McDavid"));

        assertThat(share.getToken()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(share.getName()).isEqualTo("My league");
        assertThat(share.getSeason()).isEqualTo(Season.SEASON_2026_2027);
        assertThat(share.getData().players()).hasSize(1);
        assertThat(share.getData().players().getFirst().name()).isEqualTo("Connor McDavid");
    }

    /**
     * Left to the column default, a share published from a projection on ESPN's ids was stamped
     * Yahoo, and a remap would then translate ESPN's ids as if they were Yahoo's.
     */
    @Test
    void stampsTheShareWithItsProjectionsIdSpace() {
        UserProjection onEspn = userProjectionService.create(
                userId, "On ESPN", ProjectionKind.PROJECTION, null, projectionData(), PlayerIdSpace.ESPN);

        ProjectionShare share = projectionShareService.share(
                userId, onEspn.getId(), sharedPlayers("Connor McDavid"));

        assertThat(share.getPlayerIdSpace()).isEqualTo(PlayerIdSpace.ESPN);
        assertThat(projectionShareRepository.findById(share.getId()).orElseThrow().getPlayerIdSpace())
                .isEqualTo(PlayerIdSpace.ESPN);
    }

    @Test
    void copiesTheProjectionSettingsButNotTheYahooLeagueDetails() {
        UserProjection projection = projection();

        ProjectionShare share = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Connor McDavid"));

        assertThat(share.getData().settings().scoringType()).isEqualTo(ScoringType.POINTS);
        assertThat(share.getData().settings().statWeights()).containsEntry("goals", 4.5);
        // Neither platform's league details belong on a page anyone with the link can open.
        assertThat(share.getData().settings().yahooSync()).isNull();
        assertThat(share.getData().settings().espnSync()).isNull();
        // The remembered id outlives the sync on the owner's copy — it must not outlive it here.
        assertThat(share.getData().settings().lastEspnLeagueId()).isNull();
        assertThat(share.getData().settings().playerBasis()).isNull();
        assertThat(share.getData().settings().playerPoolSyncedAt()).isNull();
        // The owner's unread notice would otherwise follow the board into every import.
        assertThat(share.getData().settings().unacknowledgedNewPlayerIds()).isNull();
    }

    /**
     * The hand ranking is the order the published page is in, so it travels with the board the
     * way the position corrections do. Stripping it would make an import of the link rank by
     * stats the author deliberately did not rank by.
     */
    @Test
    void keepsTheHandRanking() {
        UserProjection projection = projection();

        ProjectionShare share = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Connor McDavid"));

        assertThat(share.getData().settings().manualRanking().goalie().mode())
                .isEqualTo(RankingMode.MANUAL);
        assertThat(share.getData().settings().manualRanking().goalie().order())
                .containsExactly(7, 3);
    }

    @Test
    void sharingAgainReturnsTheSameLinkUntouched() {
        UserProjection projection = projection();
        ProjectionShare first = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Connor McDavid"));

        ProjectionShare second = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Nathan MacKinnon"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getToken()).isEqualTo(first.getToken());
        // A published snapshot is final: the second call hands back what was already published.
        assertThat(second.getData().players().getFirst().name()).isEqualTo("Connor McDavid");
        assertThat(projectionShareRepository.count()).isEqualTo(1L);
    }

    @Test
    void refusesToShareAProjectionThatIsNotTheCallersOwn() {
        UserProjection projection = projection();
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> projectionShareService.share(
                someoneElse, projection.getId(), sharedPlayers("Connor McDavid")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void refusesToShareUntilTheAccountHasAUsername() {
        User nameless = userRepository.save(User.create("nameless@example.com", "hash"));
        UserProjection projection = userProjectionService.create(
                nameless.getId(), "Their league", ProjectionKind.PROJECTION, null, projectionData(), PlayerIdSpace.YAHOO);

        assertThatThrownBy(() -> projectionShareService.share(
                nameless.getId(), projection.getId(), sharedPlayers("Connor McDavid")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username");
    }

    @Test
    void readsTheOwnersCurrentName_soARenameFollowsOntoLinksAlreadyShared() {
        UserProjection projection = projection();
        String token = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Connor McDavid")).getToken();

        User owner = userRepository.findById(userId).orElseThrow();
        owner.updateUsername("alexander");
        userRepository.save(owner);

        assertThat(projectionShareService.findByToken(token).authorUsername()).isEqualTo("alexander");
    }

    /**
     * The rows the caller published are the only copy kept. They are what the public page renders
     * and what an import copies, so storing the projection's own rows beside them would be the
     * same board twice.
     */
    /**
     * Taken from the stored projection rather than the caller, like the settings: what a link
     * publishes has to be the projection it points at, and an import reads these to inherit them.
     */
    @Test
    void copiesThePositionsTheOwnerCorrected() {
        UserProjection projection = projection();

        ProjectionShare share = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Connor McDavid"));

        assertThat(share.getData().positionOverrides()).containsExactly(
                new PositionOverride(1, List.of(SkaterPosition.C, SkaterPosition.LW)));
    }

    @Test
    void storesOnlyTheRowsItWasGiven() {
        UserProjection projection = projection();

        ProjectionShare share = projectionShareService.share(
                userId, projection.getId(), sharedPlayers("Connor McDavid"));

        assertThat(share.getData().players())
                .hasSize(1)
                .allSatisfy(player -> assertThat(player.stats()).isNotNull());
    }
}
