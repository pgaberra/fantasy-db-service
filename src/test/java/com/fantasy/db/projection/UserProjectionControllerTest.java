package com.fantasy.db.projection;

import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProjectionController.class)
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

    private UserProjection projection(String name) {
        ProjectionData data = new ProjectionData(
                new ProjectionSettings(ScoringType.POINTS, Map.of("goals", 4.5), List.of("goals"),
                        List.of("gp"), Map.of(), Map.of("goals", 0), true, 12, null, null),
                List.of(new PlayerProjection(1, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)))));
        return new UserProjection(PROJECTION_ID, USER_ID, name, Season.SEASON_2026_2027, data,
                Instant.now(), Instant.now());
    }

    @Test
    void listReturnsSummaries() throws Exception {
        when(userProjectionService.findAll(USER_ID)).thenReturn(List.of(projection("My league")));

        mockMvc.perform(get("/api/v1/users/{userId}/projections", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My league"))
                .andExpect(jsonPath("$[0].season").value("20262027"))
                .andExpect(jsonPath("$[0].id").value(PROJECTION_ID.toString()));
    }

    @Test
    void getReturnsProjectionWithData() throws Exception {
        when(userProjectionService.findById(USER_ID, PROJECTION_ID)).thenReturn(projection("My league"));

        mockMvc.perform(get("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My league"))
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
        when(userProjectionService.create(eq(USER_ID), eq("My league"), any()))
                .thenReturn(projection("My league"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league"))
                .andExpect(jsonPath("$.season").value("20262027"));
    }

    @Test
    void createReturns400OnBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"My league\"", "\"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409OnDuplicateName() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isNoContent());
    }
}
