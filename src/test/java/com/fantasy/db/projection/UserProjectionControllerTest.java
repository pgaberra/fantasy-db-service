package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.DraftPick;
import com.fantasy.db.projection.dto.DraftState;
import com.fantasy.db.projection.dto.DraftTeam;
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
                        List.of("gp"), Map.of(), Map.of("goals", 0), true, 12, null, null, null, null, null, null, null),
                List.of(new PlayerProjection(1, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)))),
                draft);
        return new UserProjection(PROJECTION_ID, USER_ID, name, kind, null, Season.SEASON_2026_2027, data,
                null, null, Instant.now(), Instant.now());
    }

    private static DraftState draft(Instant finishedAt) {
        return new DraftState(
                List.of(new DraftTeam("t1", "Me", true)),
                List.of("t1"),
                List.of(new DraftPick(1, "t1")),
                finishedAt);
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
    void listExposesTheKindSoCallersCanTellPresetDraftsApart() throws Exception {
        when(userProjectionService.findAll(USER_ID)).thenReturn(
                List.of(projection("Last Season's Stats", draft(null), ProjectionKind.PRESET_DRAFT)));

        mockMvc.perform(get("/api/v1/users/{userId}/projections", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("preset_draft"));
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
        when(userProjectionService.create(eq(USER_ID), eq("My league"), eq(ProjectionKind.PRESET_DRAFT), any(), any(), any()))
                .thenReturn(projection("My league", null, ProjectionKind.PRESET_DRAFT));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"name\": \"My league\",",
                                "\"name\": \"My league\", \"kind\": \"preset_draft\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("preset_draft"));

        verify(userProjectionService)
                .create(eq(USER_ID), eq("My league"), eq(ProjectionKind.PRESET_DRAFT), any(), any(), any());
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
    void createReturns409WhenUserAlreadyHasProjection() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"), eq(ProjectionKind.PROJECTION), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("User already has a projection"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    /**
     * An autosave that only moved a stat weight sends settings alone — about a kilobyte instead of
     * the ~0.5 MB of player rows it did not touch.
     */
    @Test
    void updateAcceptsABodyWithoutPlayers() throws Exception {
        ArgumentCaptor<UpdateProjectionData> sent = ArgumentCaptor.forClass(UpdateProjectionData.class);
        when(userProjectionService.update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), any()))
                .thenReturn(projection("My league"));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_WITHOUT_PLAYERS))
                .andExpect(status().isOk());

        verify(userProjectionService).update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), sent.capture());
        assertThat(sent.getValue().players()).isNull();
    }

    @Test
    void updateStillAcceptsABodyWithPlayers() throws Exception {
        ArgumentCaptor<UpdateProjectionData> sent = ArgumentCaptor.forClass(UpdateProjectionData.class);
        when(userProjectionService.update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), any()))
                .thenReturn(projection("My league"));

        mockMvc.perform(put("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        verify(userProjectionService).update(eq(USER_ID), eq(PROJECTION_ID), eq("My league"), sent.capture());
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
}
