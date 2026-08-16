package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.ScoringType;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionSettings;
import com.fantasy.db.share.dto.SharedPlayer;
import com.fantasy.db.share.dto.SharedProjectionData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProjectionShareController.class, SharedProjectionController.class})
@AutoConfigureMockMvc(addFilters = false)
class ProjectionShareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectionShareService projectionShareService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SHARE_PATH =
            "/api/v1/users/" + USER_ID + "/projections/" + PROJECTION_ID + "/share";

    private static final String VALID_BODY = """
            {
              "players": [
                { "playerId": 1, "name": "Connor McDavid", "teamAbbrev": "EDM", "positions": ["C"],
                  "headshot": "https://example.test/mcdavid.png",
                  "type": "skater", "rank": 1, "value": 412.5,
                  "stats": { "utility": { "gp": 82 }, "scoring": { "goals": 64 } } }
              ]
            }
            """;

    private static final String BODY_WITH_NAMELESS_PLAYER = """
            {
              "players": [
                { "playerId": 1, "name": "", "type": "skater", "rank": 1, "value": 412.5,
                  "stats": { "utility": { "gp": 82 }, "scoring": { "goals": 64 } } }
              ]
            }
            """;

    private static ProjectionShare share() {
        ProjectionSettings settings = new ProjectionSettings(
                ScoringType.POINTS, Map.of("goals", 4.5), List.of("goals"), List.of("gp"),
                Map.of(), Map.of("goals", 0), true, 12, null, null, null, null, null, null);
        SharedPlayer mcDavid = new SharedPlayer(
                1, "Connor McDavid", "EDM", "https://example.test/mcdavid.png", List.of("C"),
                PlayerType.SKATER, 1, 412.5,
                new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)));
        return new ProjectionShare(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                PROJECTION_ID,
                USER_ID,
                "s0mErAnd0mT0k3nV4lu3ab",
                "My league",
                Season.SEASON_2026_2027,
                new SharedProjectionData(settings, List.of(mcDavid)),
                Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-02T10:00:00Z"));
    }

    @Test
    void sharesAProjection() throws Exception {
        when(projectionShareService.share(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(share());

        mockMvc.perform(put(SHARE_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("s0mErAnd0mT0k3nV4lu3ab"))
                .andExpect(jsonPath("$.viewCount").doesNotExist());
    }

    @Test
    void passesTheSubmittedRowsThrough() throws Exception {
        when(projectionShareService.share(eq(USER_ID), eq(PROJECTION_ID), any()))
                .thenReturn(share());

        mockMvc.perform(put(SHARE_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SharedPlayer>> players = ArgumentCaptor.forClass(List.class);
        verify(projectionShareService).share(eq(USER_ID), eq(PROJECTION_ID), players.capture());
        assertThat(players.getValue()).hasSize(1);
        assertThat(players.getValue().getFirst().name()).isEqualTo("Connor McDavid");
        assertThat(players.getValue().getFirst().rank()).isEqualTo(1);
    }

    @Test
    void rejectsARowWithoutAPlayerName() throws Exception {
        mockMvc.perform(put(SHARE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_WITH_NAMELESS_PLAYER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundWhenTheProjectionIsNotShared() throws Exception {
        when(projectionShareService.findByProjection(USER_ID, PROJECTION_ID))
                .thenThrow(new NoSuchElementException("No share found"));

        mockMvc.perform(get(SHARE_PATH)).andExpect(status().isNotFound());
    }

    @Test
    void servesASnapshotByToken() throws Exception {
        when(projectionShareService.findByToken("s0mErAnd0mT0k3nV4lu3ab"))
                .thenReturn(new SharedProjection(share(), "alex"));

        mockMvc.perform(get("/api/v1/shares/s0mErAnd0mT0k3nV4lu3ab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My league"))
                .andExpect(jsonPath("$.authorUsername").value("alex"))
                .andExpect(jsonPath("$.season").value("20262027"))
                .andExpect(jsonPath("$.data.players[0].name").value("Connor McDavid"))
                .andExpect(jsonPath("$.data.players[0].rank").value(1));
    }

    @Test
    void returnsNotFoundForAnUnknownToken() throws Exception {
        when(projectionShareService.findByToken("nope"))
                .thenThrow(new NoSuchElementException("No share found for that token"));

        mockMvc.perform(get("/api/v1/shares/nope")).andExpect(status().isNotFound());
    }
}
