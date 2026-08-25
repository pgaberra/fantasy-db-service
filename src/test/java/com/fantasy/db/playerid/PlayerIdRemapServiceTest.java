package com.fantasy.db.playerid;

import com.fantasy.db.playerid.dto.PlayerIdPair;
import com.fantasy.db.playerid.dto.PlayerIdRemapRequest;
import com.fantasy.db.playerid.dto.PlayerIdRemapResponse;
import com.fantasy.db.projection.PlayerBasis;
import com.fantasy.db.projection.PlayerIdSpace;
import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.ScoringType;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.projection.dto.DraftPick;
import com.fantasy.db.projection.dto.DraftState;
import com.fantasy.db.projection.dto.DraftTeam;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.share.ProjectionShare;
import com.fantasy.db.share.ProjectionShareRepository;
import com.fantasy.db.share.dto.SharedPlayer;
import com.fantasy.db.share.dto.SharedProjectionData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PlayerIdRemapService.class)
class PlayerIdRemapServiceTest {

    /** Yahoo numbered McDavid 6743; ESPN numbers him 3895074. */
    private static final int YAHOO_MCDAVID = 6743;
    private static final int ESPN_MCDAVID = 3895074;
    private static final int YAHOO_UNKNOWN = 9999;

    @Autowired
    private PlayerIdRemapService remapService;

    @Autowired
    private UserProjectionRepository projectionRepository;

    @Autowired
    private ProjectionShareRepository shareRepository;

    private static ProjectionSettings settings() {
        return new ProjectionSettings(
                ScoringType.POINTS,
                Map.of("goals", 4.5),
                List.of("goals"),
                List.of("gp"),
                Map.of(),
                Map.of("goals", 0),
                true,
                12,
                null, null, null, null, null,
                PlayerBasis.LAST_SEASON,
                Instant.parse("2026-08-16T04:00:00Z"));
    }

    private static PlayerStats stats() {
        return new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0));
    }

    private UserProjection storeProjection(DraftState draft, int... playerIds) {
        List<PlayerProjection> players = new java.util.ArrayList<>();
        for (int playerId : playerIds) {
            players.add(new PlayerProjection(playerId, PlayerType.SKATER, stats()));
        }
        return projectionRepository.save(UserProjection.create(
                UUID.randomUUID(), "League " + UUID.randomUUID(), ProjectionKind.PROJECTION,
                Season.fromCode("20262027"), new ProjectionData(settings(), players, draft)));
    }

    private static DraftState draftWith(int... playerIds) {
        List<DraftPick> picks = new java.util.ArrayList<>();
        for (int playerId : playerIds) {
            picks.add(new DraftPick(playerId, "team-1"));
        }
        return new DraftState(List.of(new DraftTeam("team-1", "Mine", true)),
                List.of("team-1"), picks, null);
    }

    private ProjectionShare storeShare(int playerId) {
        return shareRepository.save(ProjectionShare.create(
                UUID.randomUUID(), UUID.randomUUID(), "Shared", Season.fromCode("20262027"),
                new SharedProjectionData(settings(), List.of(new SharedPlayer(
                        playerId, "Connor McDavid", "EDM", null, List.of("C"), PlayerType.SKATER,
                        1, 512.5, stats())))));
    }

    private static PlayerIdRemapRequest request(boolean dryRun, PlayerIdPair... mappings) {
        return new PlayerIdRemapRequest(List.of(mappings), dryRun);
    }

    private static PlayerIdPair mcDavid() {
        return new PlayerIdPair(YAHOO_MCDAVID, ESPN_MCDAVID);
    }

    @Test
    void remapsPlayerRowsDraftPicksAndShares() {
        UserProjection projection = storeProjection(draftWith(YAHOO_MCDAVID), YAHOO_MCDAVID);
        ProjectionShare share = storeShare(YAHOO_MCDAVID);

        PlayerIdRemapResponse response = remapService.remap(request(false, mcDavid()));

        assertThat(response.dryRun()).isFalse();
        assertThat(response.projectionsScanned()).isEqualTo(1);
        assertThat(response.playerRows().remapped()).isEqualTo(1);
        assertThat(response.draftPicks().remapped()).isEqualTo(1);
        assertThat(response.sharedRows().remapped()).isEqualTo(1);

        UserProjection stored = projectionRepository.findById(projection.getId()).orElseThrow();
        assertThat(stored.getData().players().getFirst().playerId()).isEqualTo(ESPN_MCDAVID);
        assertThat(stored.getData().draft().picks().getFirst().playerId()).isEqualTo(ESPN_MCDAVID);
        assertThat(stored.getPlayerIdSpace()).isEqualTo(PlayerIdSpace.ESPN);

        ProjectionShare storedShare = shareRepository.findById(share.getId()).orElseThrow();
        assertThat(storedShare.getData().players().getFirst().playerId()).isEqualTo(ESPN_MCDAVID);
        assertThat(storedShare.getPlayerIdSpace()).isEqualTo(PlayerIdSpace.ESPN);
    }

    /** Everything else on the row is the user's work and must come back untouched. */
    @Test
    void keepsEverythingButTheId() {
        UserProjection projection = storeProjection(null, YAHOO_MCDAVID);

        remapService.remap(request(false, mcDavid()));

        ProjectionData stored = projectionRepository.findById(projection.getId()).orElseThrow().getData();
        assertThat(stored.players().getFirst().type()).isEqualTo(PlayerType.SKATER);
        assertThat(stored.players().getFirst().stats().scoring()).containsEntry("goals", 64.0);
        assertThat(stored.settings().playerBasis()).isEqualTo(PlayerBasis.LAST_SEASON);
    }

    @Test
    void aDryRunWritesNothing() {
        UserProjection projection = storeProjection(draftWith(YAHOO_MCDAVID), YAHOO_MCDAVID);

        PlayerIdRemapResponse response = remapService.remap(request(true, mcDavid()));

        assertThat(response.dryRun()).isTrue();
        // It still reports what it would have done, which is the point of asking.
        assertThat(response.playerRows().remapped()).isEqualTo(1);
        UserProjection stored = projectionRepository.findById(projection.getId()).orElseThrow();
        assertThat(stored.getData().players().getFirst().playerId()).isEqualTo(YAHOO_MCDAVID);
        assertThat(stored.getPlayerIdSpace()).isEqualTo(PlayerIdSpace.YAHOO);
    }

    /** The default has to be the harmless one: an unqualified call must not rewrite anything. */
    @Test
    void anUnspecifiedDryRunIsADryRun() {
        UserProjection projection = storeProjection(null, YAHOO_MCDAVID);

        assertThat(remapService.remap(new PlayerIdRemapRequest(List.of(mcDavid()), null)).dryRun())
                .isTrue();
        assertThat(projectionRepository.findById(projection.getId()).orElseThrow()
                .getData().players().getFirst().playerId()).isEqualTo(YAHOO_MCDAVID);
    }

    /**
     * A row the crosswalk cannot place keeps its id. Invisible is recoverable; deleted is a
     * user's work gone.
     */
    @Test
    void leavesAnIdTheCrosswalkDoesNotCoverExactlyWhereItIs() {
        UserProjection projection = storeProjection(
                draftWith(YAHOO_UNKNOWN), YAHOO_MCDAVID, YAHOO_UNKNOWN);

        PlayerIdRemapResponse response = remapService.remap(request(false, mcDavid()));

        assertThat(response.playerRows()).isEqualTo(
                new PlayerIdRemapResponse.RemapCounts(1, 1));
        assertThat(response.draftPicks()).isEqualTo(
                new PlayerIdRemapResponse.RemapCounts(0, 1));
        assertThat(response.unmappedPlayerIds()).containsExactly(YAHOO_UNKNOWN);
        assertThat(projectionRepository.findById(projection.getId()).orElseThrow()
                .getData().players()).extracting(PlayerProjection::playerId)
                .containsExactlyInAnyOrder(ESPN_MCDAVID, YAHOO_UNKNOWN);
    }

    /**
     * The two id spaces overlap in range, so a second pass over an already-remapped row could
     * translate an id that was never Yahoo's.
     */
    @Test
    void doesNotTouchARowThatHasAlreadyBeenRemapped() {
        UserProjection projection = storeProjection(null, YAHOO_MCDAVID);
        remapService.remap(request(false, mcDavid()));

        PlayerIdRemapResponse second = remapService.remap(
                request(false, mcDavid(), new PlayerIdPair(ESPN_MCDAVID, 1)));

        assertThat(second.projectionsScanned()).isZero();
        assertThat(projectionRepository.findById(projection.getId()).orElseThrow()
                .getData().players().getFirst().playerId()).isEqualTo(ESPN_MCDAVID);
    }

    @Test
    void refusesACrosswalkThatMapsOneIdTwoWays() {
        storeProjection(null, YAHOO_MCDAVID);

        assertThatThrownBy(() -> remapService.remap(
                request(false, mcDavid(), new PlayerIdPair(YAHOO_MCDAVID, 12345))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(YAHOO_MCDAVID));
    }

    /** The same pair listed twice is not a contradiction, just a caller repeating itself. */
    @Test
    void acceptsTheSamePairTwice() {
        storeProjection(null, YAHOO_MCDAVID);

        assertThat(remapService.remap(request(false, mcDavid(), mcDavid())).playerRows().remapped())
                .isEqualTo(1);
    }
}
