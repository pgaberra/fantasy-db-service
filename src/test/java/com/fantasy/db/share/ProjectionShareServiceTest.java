package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.ScoringType;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionService;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.projection.dto.YahooSync;
import com.fantasy.db.share.dto.SharedPlayer;
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

    private final UUID userId = UUID.randomUUID();

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
                new YahooSync("Alexander's Beer League", "453.l.12345", Instant.parse("2026-08-01T10:00:00Z")));
        PlayerProjection mcDavid = new PlayerProjection(
                1, PlayerType.SKATER, new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)));
        return new ProjectionData(settings, List.of(mcDavid), null);
    }

    private static List<SharedPlayer> sharedPlayers(String topName) {
        return List.of(new SharedPlayer(
                1,
                topName,
                "EDM",
                List.of("C"),
                PlayerType.SKATER,
                1,
                412.5,
                new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0))));
    }

    private UserProjection projection() {
        return userProjectionService.create(userId, "My league", ProjectionKind.PROJECTION, projectionData());
    }

    @Test
    void sharesAProjectionUnderAnUnguessableToken() {
        UserProjection projection = projection();

        ProjectionShare share = projectionShareService.share(
                userId, projection.getId(), "Alex", sharedPlayers("Connor McDavid"));

        assertThat(share.getToken()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(share.getAuthorAlias()).isEqualTo("Alex");
        assertThat(share.getName()).isEqualTo("My league");
        assertThat(share.getSeason()).isEqualTo(Season.SEASON_2026_2027);
        assertThat(share.getData().players()).hasSize(1);
        assertThat(share.getData().players().getFirst().name()).isEqualTo("Connor McDavid");
    }

    @Test
    void copiesTheProjectionSettingsButNotTheYahooLeagueDetails() {
        UserProjection projection = projection();

        ProjectionShare share = projectionShareService.share(
                userId, projection.getId(), null, sharedPlayers("Connor McDavid"));

        assertThat(share.getData().settings().scoringType()).isEqualTo(ScoringType.POINTS);
        assertThat(share.getData().settings().statWeights()).containsEntry("goals", 4.5);
        assertThat(share.getData().settings().yahooSync()).isNull();
    }

    @Test
    void resharingKeepsTheTokenAndRefreshesTheSnapshot() {
        UserProjection projection = projection();
        ProjectionShare first = projectionShareService.share(
                userId, projection.getId(), "Alex", sharedPlayers("Connor McDavid"));

        ProjectionShare second = projectionShareService.share(
                userId, projection.getId(), "Alexander", sharedPlayers("Nathan MacKinnon"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getToken()).isEqualTo(first.getToken());
        assertThat(second.getAuthorAlias()).isEqualTo("Alexander");
        assertThat(second.getData().players().getFirst().name()).isEqualTo("Nathan MacKinnon");
        assertThat(projectionShareRepository.count()).isEqualTo(1L);
    }

    @Test
    void unsharingKillsTheLinkAndSharingAgainMintsANewToken() {
        UserProjection projection = projection();
        String originalToken = projectionShareService.share(
                userId, projection.getId(), null, sharedPlayers("Connor McDavid")).getToken();

        projectionShareService.unshare(userId, projection.getId());

        assertThatThrownBy(() -> projectionShareService.findByToken(originalToken))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(projectionShareService.share(
                userId, projection.getId(), null, sharedPlayers("Connor McDavid")).getToken())
                .isNotEqualTo(originalToken);
    }

    @Test
    void refusesToShareAProjectionThatIsNotTheCallersOwn() {
        UserProjection projection = projection();
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> projectionShareService.share(
                someoneElse, projection.getId(), null, sharedPlayers("Connor McDavid")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void refusesToUnshareSomeoneElsesProjection() {
        UserProjection projection = projection();
        projectionShareService.share(userId, projection.getId(), null, sharedPlayers("Connor McDavid"));
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> projectionShareService.unshare(someoneElse, projection.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
