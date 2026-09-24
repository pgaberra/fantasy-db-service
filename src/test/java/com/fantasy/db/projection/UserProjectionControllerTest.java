package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.DraftPick;
import com.fantasy.db.projection.dto.DraftSettings;
import com.fantasy.db.projection.dto.DraftState;
import com.fantasy.db.projection.dto.DraftTeam;
import com.fantasy.db.projection.dto.ManualRanking;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.projection.dto.UpdateProjectionData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProjectionController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProjectionService userProjectionService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String VALID_BODY = """
            {
              "name": "My league",
              "playerIdSpace": "espn",
              "data": {
                "settings": {
                  "scoringType": "points",
                  "statWeights": { "goals": 4.5 },
                  "activeScoringColumns": ["goals"],
                  "activeUtilityColumns": ["gp"],
                  "scaleSettings": {},
                  "decimalSettings": { "goals": 0 },
                  "useDefaultDecimals": true
                },
                "players": [
                  { "playerId": 1, "type": "skater",
                    "stats": { "utility": { "gp": 82 }, "scoring": { "goals": 64 } } }
                ]
              }
            }
            """;

    private static final String START_DRAFT_BODY = """
            {
              "name": "My mock",
              "data": {
                "settings": {
                  "scoringType": "points",
                  "statWeights": { "goals": 4.5 },
                  "activeScoringColumns": ["goals"],
                  "activeUtilityColumns": ["gp"],
                  "scaleSettings": {},
                  "decimalSettings": { "goals": 0 },
                  "useDefaultDecimals": true
                },
                "players": []
              }
            }
            """;

    private static final String BODY_WITHOUT_PLAYERS = """
            {
              "name": "My league",
              "playerIdSpace": "espn",
              "data": {
                "settings": {
                  "scoringType": "points",
                  "statWeights": { "goals": 4.5 },
                  "activeScoringColumns": ["goals"],
                  "activeUtilityColumns": ["gp"],
                  "scaleSettings": {},
                  "decimalSettings": { "goals": 0 },
                  "useDefaultDecimals": true
                }
              }
            }
            """;

    private UserProjection projection(String name) {
        return projection(name, null, ProjectionKind.PROJECTION);
    }

    private UserProjection projection(String name, DraftState draft) {
        return projection(name, draft, ProjectionKind.PROJECTION);
    }

    private UserProjection projection(String name, DraftState draft, ProjectionKind kind) {
        ProjectionData data = new ProjectionData(
                new ProjectionSettings(ScoringType.POINTS, Map.of("goals", 4.5), List.of("goals"),
                        List.of("gp"), Map.of(), Map.of("goals", 0), true, 12, null, null, null, null, null, null, null, null, null),
                List.of(new PlayerProjection(1, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)))),
                draft,
                null);
        return new UserProjection(PROJECTION_ID, USER_ID, name, kind, null, Season.SEASON_2026_2027, data,
                null, null, Instant.now(), Instant.now());
    }

    private static DraftState draft(Instant finishedAt) {
        return new DraftState(
                List.of(new DraftTeam("t1", "Me", true)),
                List.of("t1"),
                List.of(new DraftPick(1, "t1")),
                finishedAt,
                null,
                null);
    }

    @Test
    void listReturnsSummaries() throws Exception {
        when(userProjectionService.findAll(USER_ID)).thenReturn(List.of(projection("My league")));

        mockMvc.perform(get("/api/v1/users/{userId}/projections", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My league"))
                .andExpect(jsonPath("$[0].season").value("20262027"))
                .andExpect(jsonPath("$[0].id").value(PROJECTION_ID.toString()))
                .andExpect(jsonPath("$[0].kind").value("projection"))
                .andExpect(jsonPath("$[0].draftStatus").value("none"));
    }

    @Test
    void listExposesTheKindSoCallersCanTellDraftsApartFromBoards() throws Exception {
        when(userProjectionService.findAll(USER_ID)).thenReturn(
                List.of(projection("Last Season's Stats", draft(null), ProjectionKind.DRAFT)));

        mockMvc.perform(get("/api/v1/users/{userId}/projections", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("draft"));
    }

    @Test
    void listReflectsDraftStatus() throws Exception {
        when(userProjectionService.findAll(USER_ID)).thenReturn(List.of(
                projection("Finished", draft(Instant.parse("2026-07-15T10:00:00Z"))),
                projection("In progress", draft(null))));

        mockMvc.perform(get("/api/v1/users/{userId}/projections", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].draftStatus").value("finished"))
                .andExpect(jsonPath("$[1].draftStatus").value("in_progress"));
    }

    @Test
    void getReturnsProjectionWithData() throws Exception {
        when(userProjectionService.findById(USER_ID, PROJECTION_ID)).thenReturn(projection("My league"));

        mockMvc.perform(get("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My league"))
                .andExpect(jsonPath("$.kind").value("projection"))
                .andExpect(jsonPath("$.data.settings.scoringType").value("points"))
                .andExpect(jsonPath("$.data.players[0].stats.scoring.goals").value(64.0));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(userProjectionService.findById(USER_ID, PROJECTION_ID))
                .thenThrow(new NoSuchElementException("nope"));

        mockMvc.perform(get("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"), eq(ProjectionKind.PROJECTION), any(), any(), any()))
                .thenReturn(projection("My league"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league"))
                .andExpect(jsonPath("$.season").value("20262027"));
    }

    @Test
    void createStoresTheRequestedKind() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"), eq(ProjectionKind.DRAFT), any(), any(), any()))
                .thenReturn(projection("My league", null, ProjectionKind.DRAFT));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"name\": \"My league\",",
                                "\"name\": \"My league\", \"kind\": \"draft\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("draft"));

        verify(userProjectionService)
                .create(eq(USER_ID), eq("My league"), eq(ProjectionKind.DRAFT), any(), any(), any());
    }

    @Test
    void createReturns400OnBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"My league\"", "\"\"")))
                .andExpect(status().isBadRequest());
    }

    /**
     * A board is every player in the league — around 1,500 rows — so the cap sits well above that
     * and exists to stop an oversized body being parsed and stored rather than to limit anyone.
     */
    @Test
    void createReturns400OnMorePlayerRowsThanAnyLeagueHas() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayerRows(2001)))
                .andExpect(status().isBadRequest());

        verify(userProjectionService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createAcceptsAsManyRowsAsTheLargestPlayerPoolHas() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"), any(), any(), any(), any()))
                .thenReturn(projection("My league"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithPlayerRows(2000)))
                .andExpect(status().isCreated());
    }

    /** Built rather than patched into {@link #VALID_BODY}, so it does not depend on that layout. */
    private static String bodyWithPlayerRows(int rows) {
        String players = IntStream.rangeClosed(1, rows)
                .mapToObj(id -> ("{\"playerId\":%d,\"type\":\"skater\","
                        + "\"stats\":{\"utility\":{\"gp\":82},\"scoring\":{\"goals\":64}}}").formatted(id))
                .collect(Collectors.joining(","));
        return ("{\"name\":\"My league\",\"playerIdSpace\":\"espn\",\"data\":{"
                + "\"settings\":{\"scoringType\":\"points\",\"statWeights\":{\"goals\":4.5},"
                + "\"activeScoringColumns\":[\"goals\"],\"activeUtilityColumns\":[\"gp\"],"
                + "\"scaleSettings\":{},\"decimalSettings\":{\"goals\":0},\"useDefaultDecimals\":true},"
                + "\"players\":[%s]}}").formatted(players);
    }

    @Test
    void createStoresAHandRankedOrder() throws Exception {
        ArgumentCaptor<ProjectionData> sent = ArgumentCaptor.forClass(ProjectionData.class);
        when(userProjectionService.create(eq(USER_ID), eq("My league"), any(), any(), any(), any()))
                .thenReturn(projection("My league"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithManualRanking("[12, 7, 3]")))
                .andExpect(status().isCreated());

        verify(userProjectionService)
                .create(eq(USER_ID), eq("My league"), any(), any(), sent.capture(), any());
        ManualRanking ranking = sent.getValue().settings().manualRanking();
        assertThat(ranking.skater().mode()).isEqualTo(RankingMode.PROJECTED);
        assertThat(ranking.goalie().mode()).isEqualTo(RankingMode.MANUAL);
        assertThat(ranking.goalie().order()).containsExactly(12, 7, 3);
    }

    /** The cap matches the player rows': an order can name every player and no more. */
    @Test
    void createReturns400OnAHandRankedOrderLongerThanThePlayerPool() throws Exception {
        String order = IntStream.rangeClosed(1, 2001)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithManualRanking("[" + order + "]")))
                .andExpect(status().isBadRequest());

        verify(userProjectionService, never()).create(any(), any(), any(), any(), any(), any());
    }

    private static String bodyWithManualRanking(String goalieOrder) {
        return VALID_BODY.replace("\"useDefaultDecimals\": true",
                "\"useDefaultDecimals\": true, \"manualRanking\": {"
                        + "\"skater\": { \"mode\": \"projected\" },"
                        + "\"goalie\": { \"mode\": \"manual\", \"order\": " + goalieOrder + " } }");
    }

    /**
     * A taken name is numbered rather than refused, so what the caller has to read back is the
     * name in the response, not the one it sent.
     */
    @Test
    void createAnswersWithTheNameTheProjectionWasSavedUnder() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"), eq(ProjectionKind.PROJECTION), any(), any(), any()))
                .thenReturn(projection("My league (2)"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league (2)"));
    }

    @Test
    void updateAcceptsABodyWithoutPlayers() throws Exception {
        ArgumentCaptor<UpdateProjectionData> sent = ArgumentCaptor.forClass(UpdateProjectionData.class);
        when(userProjectionService.update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), any(), eq(PlayerIdSpace.ESPN)))
                .thenReturn(projection("My league"));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_WITHOUT_PLAYERS))
                .andExpect(status().isOk());

        verify(userProjectionService).update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), sent.capture(), eq(PlayerIdSpace.ESPN));
        assertThat(sent.getValue().players()).isNull();
    }

    /** A body whose draft carries its own league, with the goalie minimum set to {@code games}. */
    private static String bodyWithDraftLeague(int games) {
        return """
                {
                  "name": "My league",
                  "playerIdSpace": "espn",
                  "data": {
                    "settings": {
                      "scoringType": "points",
                      "statWeights": { "goals": 4.5 },
                      "activeScoringColumns": ["goals"],
                      "activeUtilityColumns": ["gp"],
                      "scaleSettings": {},
                      "decimalSettings": { "goals": 0 },
                      "useDefaultDecimals": true
                    },
                    "draft": {
                      "teams": [{ "id": "t1", "name": "Me", "mine": true }],
                      "order": ["t1"],
                      "picks": [],
                      "settings": {
                        "scoringType": "category",
                        "statWeights": { "goals": 4.5 },
                        "activeScoringColumns": ["goals", "hits"],
                        "activeUtilityColumns": ["gp"],
                        "leagueSize": 10,
                        "rosterSlots": { "c": 2, "lw": 2, "rw": 2, "d": 4, "util": 1, "bn": 4, "g": 2 },
                        "minGoalieGames": %d,
                        "espnSync": { "leagueName": "Puck Luck", "leagueId": "42", "syncedAt": "2026-09-17T08:00:00Z" }
                      }
                    }
                  }
                }
                """.formatted(games);
    }

    /**
     * A draft's league is its own, so that setting one up never rewrites the projection it is played
     * against — and a full NHL season is 84 games, so that is the highest goalie minimum there is.
     */
    @Test
    void updateKeepsTheLeagueADraftHoldsOfItsOwn() throws Exception {
        ArgumentCaptor<UpdateProjectionData> sent = ArgumentCaptor.forClass(UpdateProjectionData.class);
        when(userProjectionService.update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), any(), eq(PlayerIdSpace.ESPN)))
                .thenReturn(projection("My league"));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithDraftLeague(84)))
                .andExpect(status().isOk());

        verify(userProjectionService).update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), sent.capture(), eq(PlayerIdSpace.ESPN));
        DraftSettings league = sent.getValue().draft().settings();
        assertThat(league.scoringType()).isEqualTo(ScoringType.CATEGORY);
        assertThat(league.leagueSize()).isEqualTo(10);
        assertThat(league.minGoalieGames()).isEqualTo(84);
        assertThat(league.espnSync().leagueId()).isEqualTo("42");
    }

    /**
     * The sync switch is saved with the draft and served back with it, so the page that reopens
     * the draft reads whether it was left following its league.
     */
    @Test
    void updateTakesAndReturnsWhetherADraftFollowsItsLeague() throws Exception {
        ArgumentCaptor<UpdateProjectionData> sent = ArgumentCaptor.forClass(UpdateProjectionData.class);
        DraftState followed = new DraftState(List.of(new DraftTeam("t1", "Me", true)), List.of("t1"),
                List.of(), null, null, true);
        when(userProjectionService.update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), any(), eq(PlayerIdSpace.ESPN)))
                .thenReturn(projection("My league", followed, ProjectionKind.DRAFT));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FOLLOWING_DRAFT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft.following").value(true));

        verify(userProjectionService).update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), sent.capture(), eq(PlayerIdSpace.ESPN));
        assertThat(sent.getValue().draft()).isEqualTo(followed);
    }

    /** A draft saved before the switch was stored says nothing about it, rather than false. */
    @Test
    void getLeavesFollowingOutOfADraftThatNeverFollowed() throws Exception {
        when(userProjectionService.findById(USER_ID, PROJECTION_ID))
                .thenReturn(projection("My league", draft(null), ProjectionKind.DRAFT));

        mockMvc.perform(get("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft.picks[0].playerId").value(1))
                .andExpect(jsonPath("$.data.draft.following").doesNotExist());
    }

    private static final String FOLLOWING_DRAFT_BODY = """
            {
              "name": "My league",
              "playerIdSpace": "espn",
              "data": {
                "settings": {
                  "scoringType": "points",
                  "statWeights": { "goals": 4.5 },
                  "activeScoringColumns": ["goals"],
                  "activeUtilityColumns": ["gp"],
                  "scaleSettings": {},
                  "decimalSettings": { "goals": 0 },
                  "useDefaultDecimals": true
                },
                "draft": {
                  "teams": [{ "id": "t1", "name": "Me", "mine": true }],
                  "order": ["t1"],
                  "picks": [],
                  "following": true
                }
              }
            }
            """;

    @Test
    void updateRejectsAGoalieMinimumLongerThanASeason() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithDraftLeague(85)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStillAcceptsABodyWithPlayers() throws Exception {
        ArgumentCaptor<UpdateProjectionData> sent = ArgumentCaptor.forClass(UpdateProjectionData.class);
        when(userProjectionService.update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), any(), eq(PlayerIdSpace.ESPN)))
                .thenReturn(projection("My league"));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        verify(userProjectionService).update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), sent.capture(), eq(PlayerIdSpace.ESPN));
        assertThat(sent.getValue().players()).hasSize(1);
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isNoContent());
    }

    /**
     * The space is the caller's to state, not ours to assume: the rows are keyed by whichever
     * platform's pool filled them, and a projection stamped with the wrong one is only found out
     * when a remap translates ids that were never in the space it assumed.
     */
    @Test
    void forwardsThePlayerIdSpaceTheCallerStated() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"),
                eq(ProjectionKind.PROJECTION), any(), any(), any()))
                .thenReturn(projection("My league"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());

        verify(userProjectionService).create(eq(USER_ID), eq("My league"),
                eq(ProjectionKind.PROJECTION), any(), any(), eq(PlayerIdSpace.ESPN));
    }

    @Test
    void rejectsACreateThatDoesNotSayWhichIdSpaceTheRowsAreIn() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"playerIdSpace\": \"espn\",", "")))
                .andExpect(status().isBadRequest());

        verify(userProjectionService, never()).create(any(), any(), any(), any(), any(), any());
    }
    @Test
    void startDraftReturns201WithTheDraftTheServerSaved() throws Exception {
        when(userProjectionService.startDraft(eq(USER_ID), eq(PROJECTION_ID), eq("My mock"), any()))
                .thenReturn(projection("My mock", null, ProjectionKind.DRAFT));

        mockMvc.perform(post("/api/v1/users/{userId}/projections/{id}/drafts", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_DRAFT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("draft"))
                .andExpect(jsonPath("$.name").value("My mock"));
    }

    /**
     * The name is the server's to settle, so the caller may leave it out and take the board's.
     */
    @Test
    void startDraftMayOmitTheName() throws Exception {
        when(userProjectionService.startDraft(eq(USER_ID), eq(PROJECTION_ID), eq(null), any()))
                .thenReturn(projection("My league", null, ProjectionKind.DRAFT));

        mockMvc.perform(post("/api/v1/users/{userId}/projections/{id}/drafts", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_DRAFT_BODY.replace("\"name\": \"My mock\",", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league"));
    }

    @Test
    void startDraftReturns404WhenTheBoardIsNotTheUsers() throws Exception {
        when(userProjectionService.startDraft(any(), any(), any(), any()))
                .thenThrow(new NoSuchElementException("No projection found with id: " + PROJECTION_ID));

        mockMvc.perform(post("/api/v1/users/{userId}/projections/{id}/drafts", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(START_DRAFT_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void renameReturnsTheSavedName() throws Exception {
        when(userProjectionService.rename(USER_ID, PROJECTION_ID, "Mock #3", false))
                .thenReturn(projection("Mock #3", null, ProjectionKind.DRAFT));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}/name", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mock #3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mock #3"));
    }

    /** A derived name (a league sync's) takes the other path, which may decline to rename at all. */
    @Test
    void renameRoutesADerivedNameToTheDerivedPath() throws Exception {
        when(userProjectionService.renameDerived(USER_ID, PROJECTION_ID, "Beer League"))
                .thenReturn(projection("Mock #3", null, ProjectionKind.DRAFT));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}/name", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beer League\", \"derived\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mock #3"));

        verify(userProjectionService, never()).rename(any(), any(), any(), anyBoolean());
    }

    @Test
    void renameReturns400OnABlankName() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}/name", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest());

        verify(userProjectionService, never()).rename(any(), any(), any(), anyBoolean());
    }

    @Test
    void renameReturns409WhenAnotherRowHoldsTheName() throws Exception {
        when(userProjectionService.rename(USER_ID, PROJECTION_ID, "Taken", false))
                .thenThrow(new DataIntegrityViolationException("User already has a projection named Taken"));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}/name", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Taken\"}"))
                .andExpect(status().isConflict());
    }
}
