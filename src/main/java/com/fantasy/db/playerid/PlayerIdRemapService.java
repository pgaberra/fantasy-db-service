package com.fantasy.db.playerid;

import com.fantasy.db.playerid.dto.PlayerIdPair;
import com.fantasy.db.playerid.dto.PlayerIdRemapRequest;
import com.fantasy.db.playerid.dto.PlayerIdRemapResponse;
import com.fantasy.db.playerid.dto.PlayerIdRemapResponse.RemapCounts;
import com.fantasy.db.projection.PlayerIdSpace;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.UserProjectionRepository;
import com.fantasy.db.projection.dto.DraftPick;
import com.fantasy.db.projection.dto.DraftState;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PositionOverride;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.share.ProjectionShare;
import com.fantasy.db.share.ProjectionShareRepository;
import com.fantasy.db.share.dto.SharedPlayer;
import com.fantasy.db.share.dto.SharedProjectionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Rewrites the player ids in saved projections and shares from one platform's numbering to
 * another's, once.
 *
 * <p>The crosswalk comes from the caller — matching Yahoo's players to ESPN's is something only
 * a service that can see both pools can do — and the writing happens here, where the rows live,
 * in one transaction.
 *
 * <p>An id the crosswalk has no entry for is <b>left exactly as it is</b>, never dropped. A row
 * the app cannot draw is invisible and recoverable; a deleted row is a user's work gone. The
 * counts say how many there were, which is what a dry run is for.
 */
@Service
public class PlayerIdRemapService {

    private static final Logger log = LoggerFactory.getLogger(PlayerIdRemapService.class);

    /** Enough to see the shape of what went unmatched without answering with a wall of ids. */
    private static final int UNMAPPED_SAMPLE = 50;

    private final UserProjectionRepository projectionRepository;
    private final ProjectionShareRepository shareRepository;

    public PlayerIdRemapService(UserProjectionRepository projectionRepository,
                                ProjectionShareRepository shareRepository) {
        this.projectionRepository = projectionRepository;
        this.shareRepository = shareRepository;
    }

    @Transactional
    public PlayerIdRemapResponse remap(PlayerIdRemapRequest request) {
        Map<Integer, Integer> crosswalk = crosswalk(request.mappings());
        boolean dryRun = request.isDryRun();
        Tally tally = new Tally();

        List<UserProjection> projections =
                projectionRepository.findAllByPlayerIdSpace(PlayerIdSpace.YAHOO.getCode());
        for (UserProjection projection : projections) {
            ProjectionData remapped = remap(projection.getData(), crosswalk, tally);
            if (!dryRun) {
                projection.remapPlayerIds(remapped, PlayerIdSpace.ESPN);
            }
        }

        List<ProjectionShare> shares =
                shareRepository.findAllByPlayerIdSpace(PlayerIdSpace.YAHOO.getCode());
        for (ProjectionShare share : shares) {
            SharedProjectionData remapped = remap(share.getData(), crosswalk, tally);
            if (!dryRun) {
                share.remapPlayerIds(remapped, PlayerIdSpace.ESPN);
            }
        }

        PlayerIdRemapResponse response = new PlayerIdRemapResponse(
                dryRun,
                projections.size(),
                new RemapCounts(tally.playerRowsRemapped, tally.playerRowsUnmapped),
                new RemapCounts(tally.draftPicksRemapped, tally.draftPicksUnmapped),
                new RemapCounts(tally.positionOverridesRemapped, tally.positionOverridesUnmapped),
                shares.size(),
                new RemapCounts(tally.sharedRowsRemapped, tally.sharedRowsUnmapped),
                tally.unmappedSample());
        log.info("Player id remap ({}): {} projections, {} shares; {} player rows and {} draft "
                        + "picks remapped, {} rows left on an id the crosswalk did not cover",
                dryRun ? "dry run" : "applied", projections.size(), shares.size(),
                tally.playerRowsRemapped, tally.draftPicksRemapped,
                tally.playerRowsUnmapped + tally.draftPicksUnmapped + tally.sharedRowsUnmapped
                        + tally.positionOverridesUnmapped);
        return response;
    }

    private static Map<Integer, Integer> crosswalk(List<PlayerIdPair> mappings) {
        Map<Integer, Integer> crosswalk = new HashMap<>();
        for (PlayerIdPair pair : mappings) {
            Integer previous = crosswalk.put(pair.from(), pair.to());
            if (previous != null && previous != pair.to()) {
                // Two destinations for one stored id is not something to pick a winner for:
                // half the rows would silently become the wrong player.
                throw new IllegalArgumentException(
                        "Player id " + pair.from() + " is mapped to both " + previous
                                + " and " + pair.to());
            }
        }
        return crosswalk;
    }

    private ProjectionData remap(ProjectionData data, Map<Integer, Integer> crosswalk, Tally tally) {
        List<PlayerProjection> players = new ArrayList<>(data.players().size());
        for (PlayerProjection player : data.players()) {
            Integer mapped = crosswalk.get(player.playerId());
            tally.player(mapped != null, player.playerId());
            players.add(mapped == null
                    ? player
                    : new PlayerProjection(mapped, player.type(), player.stats()));
        }
        return new ProjectionData(remap(data.settings(), crosswalk), players,
                remap(data.draft(), crosswalk, tally),
                remapOverrides(data.positionOverrides(), crosswalk, tally));
    }

    /**
     * The new players the owner has yet to acknowledge are named by id, so they move with the
     * rows they point at. An id the crosswalk does not cover stays, as a row's does.
     */
    private static ProjectionSettings remap(ProjectionSettings settings, Map<Integer, Integer> crosswalk) {
        if (settings == null || settings.unacknowledgedNewPlayerIds() == null) {
            return settings;
        }
        return settings.withUnacknowledgedNewPlayerIds(settings.unacknowledgedNewPlayerIds().stream()
                .map(playerId -> crosswalk.getOrDefault(playerId, playerId))
                .toList());
    }

    private List<PositionOverride> remapOverrides(List<PositionOverride> overrides,
                                                  Map<Integer, Integer> crosswalk, Tally tally) {
        if (overrides == null) {
            return null;
        }
        List<PositionOverride> remapped = new ArrayList<>(overrides.size());
        for (PositionOverride override : overrides) {
            Integer mapped = crosswalk.get(override.playerId());
            tally.positionOverride(mapped != null, override.playerId());
            remapped.add(mapped == null
                    ? override
                    : new PositionOverride(mapped, override.positions()));
        }
        return remapped;
    }

    private DraftState remap(DraftState draft, Map<Integer, Integer> crosswalk, Tally tally) {
        if (draft == null) {
            return null;
        }
        List<DraftPick> picks = new ArrayList<>(draft.picks().size());
        for (DraftPick pick : draft.picks()) {
            Integer mapped = crosswalk.get(pick.playerId());
            tally.draftPick(mapped != null, pick.playerId());
            picks.add(mapped == null ? pick : new DraftPick(mapped, pick.teamId()));
        }
        return new DraftState(draft.teams(), draft.order(), picks, draft.finishedAt());
    }

    private SharedProjectionData remap(SharedProjectionData data, Map<Integer, Integer> crosswalk,
                                       Tally tally) {
        List<SharedPlayer> players = new ArrayList<>(data.players().size());
        for (SharedPlayer player : data.players()) {
            Integer mapped = crosswalk.get(player.playerId());
            tally.sharedRow(mapped != null, player.playerId());
            players.add(mapped == null
                    ? player
                    : new SharedPlayer(mapped, player.name(), player.teamAbbrev(),
                            player.headshot(), player.positions(), player.type(), player.rank(),
                            player.value(), player.stats()));
        }
        return new SharedProjectionData(data.settings(), players,
                remapOverrides(data.positionOverrides(), crosswalk, tally));
    }


    private static final class Tally {

        private int playerRowsRemapped;
        private int playerRowsUnmapped;
        private int draftPicksRemapped;
        private int draftPicksUnmapped;
        private int sharedRowsRemapped;
        private int sharedRowsUnmapped;
        private int positionOverridesRemapped;
        private int positionOverridesUnmapped;
        private final TreeSet<Integer> unmapped = new TreeSet<>();

        void player(boolean mapped, int playerId) {
            if (mapped) {
                playerRowsRemapped++;
            } else {
                playerRowsUnmapped++;
                unmapped.add(playerId);
            }
        }

        void draftPick(boolean mapped, int playerId) {
            if (mapped) {
                draftPicksRemapped++;
            } else {
                draftPicksUnmapped++;
                unmapped.add(playerId);
            }
        }

        void positionOverride(boolean mapped, int playerId) {
            if (mapped) {
                positionOverridesRemapped++;
            } else {
                positionOverridesUnmapped++;
                unmapped.add(playerId);
            }
        }

        void sharedRow(boolean mapped, int playerId) {
            if (mapped) {
                sharedRowsRemapped++;
            } else {
                sharedRowsUnmapped++;
                unmapped.add(playerId);
            }
        }

        List<Integer> unmappedSample() {
            return unmapped.stream().limit(UNMAPPED_SAMPLE).toList();
        }
    }
}
