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
import com.fantasy.db.projection.dto.ManualRanking;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerTypeRanking;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>The exception is an uncovered id that some other player is being moved <b>to</b>. The two
 * numberings overlap, so Yahoo's 5738 (Martin Frk, whom ESPN does not carry) is ESPN's Brian
 * Dumoulin: kept, Frk's row would sit under Dumoulin's id and be drawn as him, which is neither
 * invisible nor recoverable. Such a row is removed. A draft pick like it is not, because that
 * would rewrite the draft, so any at all make an apply refuse.
 *
 * <p>It runs in either direction. The first migration moved everything from Yahoo's ids to
 * ESPN's; when Yahoo served its players again the pool went back, so the same rules apply with
 * the two sides swapped. Only rows stamped with the numbering being left are read.
 */
@Service
public class PlayerIdRemapService {

    private static final Logger log = LoggerFactory.getLogger(PlayerIdRemapService.class);

    /** Enough to see the shape of what went unmatched without answering with a wall of ids. */
    private static final int ID_SAMPLE = 50;

    private final UserProjectionRepository projectionRepository;
    private final ProjectionShareRepository shareRepository;

    public PlayerIdRemapService(UserProjectionRepository projectionRepository,
                                ProjectionShareRepository shareRepository) {
        this.projectionRepository = projectionRepository;
        this.shareRepository = shareRepository;
    }

    @Transactional
    public PlayerIdRemapResponse remap(PlayerIdRemapRequest request) {
        boolean dryRun = request.isDryRun();
        PlayerIdSpace from = request.fromSpace();
        PlayerIdSpace to = request.toSpace();
        if (from == to) {
            // Stamping rows with the numbering they already carry would mark nothing as moved
            // while translating every id: each one would become some other player.
            throw new IllegalArgumentException("A remap has to move rows between two numberings, "
                    + "not from " + from.getCode() + " to itself");
        }
        Tally tally = new Tally(crosswalk(request.mappings()));
        List<Runnable> writes = new ArrayList<>();

        List<UserProjection> projections = projectionRepository.findAllByPlayerIdSpace(from.getCode());
        for (UserProjection projection : projections) {
            ProjectionData remapped = remap(projection.getData(), tally);
            writes.add(() -> projection.remapPlayerIds(remapped, to));
        }

        List<ProjectionShare> shares = shareRepository.findAllByPlayerIdSpace(from.getCode());
        for (ProjectionShare share : shares) {
            SharedProjectionData remapped = remap(share.getData(), tally);
            writes.add(() -> share.remapPlayerIds(remapped, to));
        }

        if (!dryRun) {
            if (tally.draftPicks.colliding > 0) {
                throw new IllegalStateException(("%d draft picks name a player whose old id "
                        + "another player is being moved to (%s), and removing a pick would "
                        + "rewrite the draft. Nothing was written.")
                        .formatted(tally.draftPicks.colliding, tally.collidingSample()));
            }
            writes.forEach(Runnable::run);
        }

        PlayerIdRemapResponse response = new PlayerIdRemapResponse(
                dryRun,
                from,
                to,
                projections.size(),
                tally.playerRows.counts(),
                tally.draftPicks.counts(),
                tally.positionOverrides.counts(),
                shares.size(),
                tally.sharedRows.counts(),
                tally.unmappedSample(),
                tally.collidingSample());
        log.info("Player id remap {} to {} ({}): {} projections, {} shares; {} player rows and {} "
                        + "draft picks remapped, {} rows left on an id the crosswalk did not cover, {} "
                        + "removed for sitting on an id another player moves to",
                from == PlayerIdSpace.ESPN ? "espn" : "yahoo", to == PlayerIdSpace.ESPN ? "espn" : "yahoo",
                dryRun ? "dry run" : "applied",
                projections.size(), shares.size(),
                tally.playerRows.remapped, tally.draftPicks.remapped,
                tally.playerRows.unmapped + tally.draftPicks.unmapped + tally.sharedRows.unmapped
                        + tally.positionOverrides.unmapped,
                tally.playerRows.colliding + tally.sharedRows.colliding
                        + tally.positionOverrides.colliding);
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

    private ProjectionData remap(ProjectionData data, Tally tally) {
        List<PlayerProjection> players = new ArrayList<>(data.players().size());
        for (PlayerProjection player : data.players()) {
            Integer placed = tally.place(player.playerId(), tally.playerRows);
            if (placed != null) {
                players.add(placed == player.playerId()
                        ? player
                        : new PlayerProjection(placed, player.type(), player.stats()));
            }
        }
        return new ProjectionData(remap(data.settings(), tally), players,
                remap(data.draft(), tally),
                remapOverrides(data.positionOverrides(), tally));
    }

    /**
     * The settings name players by id in two places - the new ones the owner has yet to
     * acknowledge, and the order they put a hand-ranked player type in - so both move with the
     * rows they point at: an id the crosswalk does not cover stays, as a row's does, and one on a
     * colliding id goes, as its row does. A hand ranking left on the old numbering would order
     * the board by players who are no longer there.
     */
    private static ProjectionSettings remap(ProjectionSettings settings, Tally tally) {
        if (settings == null) {
            return null;
        }
        ProjectionSettings remapped = settings;
        if (settings.unacknowledgedNewPlayerIds() != null) {
            remapped = remapped.withUnacknowledgedNewPlayerIds(
                    remapIds(settings.unacknowledgedNewPlayerIds(), tally));
        }
        if (settings.manualRanking() != null) {
            remapped = remapped.withManualRanking(remap(settings.manualRanking(), tally));
        }
        return remapped;
    }

    private static ManualRanking remap(ManualRanking ranking, Tally tally) {
        return new ManualRanking(remap(ranking.skater(), tally), remap(ranking.goalie(), tally));
    }

    private static PlayerTypeRanking remap(PlayerTypeRanking ranking, Tally tally) {
        if (ranking.order() == null) {
            return ranking;
        }
        return new PlayerTypeRanking(ranking.mode(), remapIds(ranking.order(), tally));
    }

    private static List<Integer> remapIds(List<Integer> playerIds, Tally tally) {
        List<Integer> ids = new ArrayList<>(playerIds.size());
        for (Integer playerId : playerIds) {
            Integer placed = tally.placeUncounted(playerId);
            if (placed != null) {
                ids.add(placed);
            }
        }
        return ids;
    }

    private List<PositionOverride> remapOverrides(List<PositionOverride> overrides, Tally tally) {
        if (overrides == null) {
            return null;
        }
        List<PositionOverride> remapped = new ArrayList<>(overrides.size());
        for (PositionOverride override : overrides) {
            Integer placed = tally.place(override.playerId(), tally.positionOverrides);
            if (placed != null) {
                remapped.add(placed == override.playerId()
                        ? override
                        : new PositionOverride(placed, override.positions()));
            }
        }
        return remapped;
    }

    private DraftState remap(DraftState draft, Tally tally) {
        if (draft == null) {
            return null;
        }
        List<DraftPick> picks = new ArrayList<>(draft.picks().size());
        for (DraftPick pick : draft.picks()) {
            Integer placed = tally.place(pick.playerId(), tally.draftPicks);
            picks.add(placed == null || placed == pick.playerId()
                    ? pick
                    : new DraftPick(placed, pick.teamId()));
        }
        return new DraftState(draft.teams(), draft.order(), picks, draft.finishedAt(), draft.settings(),
                draft.following());
    }

    private SharedProjectionData remap(SharedProjectionData data, Tally tally) {
        List<SharedPlayer> players = new ArrayList<>(data.players().size());
        for (SharedPlayer player : data.players()) {
            Integer placed = tally.place(player.playerId(), tally.sharedRows);
            if (placed != null) {
                players.add(placed == player.playerId()
                        ? player
                        : new SharedPlayer(placed, player.name(), player.teamAbbrev(),
                                player.headshot(), player.positions(), player.type(), player.rank(),
                                player.value(), player.stats()));
            }
        }
        return new SharedProjectionData(data.settings(), players,
                remapOverrides(data.positionOverrides(), tally));
    }

    private static final class Counter {

        private int remapped;
        private int unmapped;
        private int colliding;

        RemapCounts counts() {
            return new RemapCounts(remapped, unmapped, colliding);
        }
    }

    private static final class Tally {

        private final Map<Integer, Integer> crosswalk;
        private final Set<Integer> destinations;
        private final Counter playerRows = new Counter();
        private final Counter draftPicks = new Counter();
        private final Counter positionOverrides = new Counter();
        private final Counter sharedRows = new Counter();
        private final TreeSet<Integer> unmapped = new TreeSet<>();
        private final TreeSet<Integer> colliding = new TreeSet<>();

        Tally(Map<Integer, Integer> crosswalk) {
            this.crosswalk = crosswalk;
            this.destinations = new HashSet<>(crosswalk.values());
        }

        /** The id a stored reference moves to, its own id if it stays, or null if it has to go. */
        Integer place(int playerId, Counter counter) {
            Integer mapped = crosswalk.get(playerId);
            if (mapped != null) {
                counter.remapped++;
                return mapped;
            }
            if (destinations.contains(playerId)) {
                counter.colliding++;
                colliding.add(playerId);
                return null;
            }
            counter.unmapped++;
            unmapped.add(playerId);
            return playerId;
        }

        Integer placeUncounted(int playerId) {
            Integer mapped = crosswalk.get(playerId);
            if (mapped != null) {
                return mapped;
            }
            return destinations.contains(playerId) ? null : playerId;
        }

        List<Integer> unmappedSample() {
            return unmapped.stream().limit(ID_SAMPLE).toList();
        }

        List<Integer> collidingSample() {
            return colliding.stream().limit(ID_SAMPLE).toList();
        }
    }
}
