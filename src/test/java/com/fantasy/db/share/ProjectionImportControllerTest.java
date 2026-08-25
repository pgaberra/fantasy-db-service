package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerType;
import com.fantasy.db.projection.ProjectionKind;
import com.fantasy.db.projection.ScoringType;
import com.fantasy.db.projection.Season;
import com.fantasy.db.projection.UserProjection;
import com.fantasy.db.projection.dto.PlayerProjection;
import com.fantasy.db.projection.dto.PlayerStats;
import com.fantasy.db.projection.dto.ProjectionData;
import com.fantasy.db.projection.dto.ProjectionSettings;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectionImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectionImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectionImportService projectionImportService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String IMPORT_PATH = "/api/v1/users/" + USER_ID + "/projections/imports";

    private static final String VALID_BODY = """
            { "token": "s0mErAnd0mT0k3nV4lu3ab" }
            """;

    private static UserProjection imported() {
        ProjectionData data = new ProjectionData(
                new ProjectionSettings(ScoringType.POINTS, Map.of("goals", 4.5), List.of("goals"),
                        List.of("gp"), Map.of(), Map.of("goals", 0), true, 12, null, null, null, null,
                        null, null, null),
                List.of(new PlayerProjection(1, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)))),
                null);
        return new UserProjection(PROJECTION_ID, USER_ID, "My league", ProjectionKind.IMPORTED,
                Season.SEASON_2026_2027, data, "s0mErAnd0mT0k3nV4lu3ab", "alex",
                Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:00:00Z"));
    }

    @Test
    void importsASharedBoard() throws Exception {
        when(projectionImportService.importFrom(eq(USER_ID), eq("s0mErAnd0mT0k3nV4lu3ab"), eq(null)))
                .thenReturn(imported());

        mockMvc.perform(post(IMPORT_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("imported"))
                .andExpect(jsonPath("$.origin.shareToken").value("s0mErAnd0mT0k3nV4lu3ab"))
                .andExpect(jsonPath("$.origin.authorUsername").value("alex"));
    }

    @Test
    void rejectsABlankToken() throws Exception {
        mockMvc.perform(post(IMPORT_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \" \" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownTokenAsNotFound() throws Exception {
        when(projectionImportService.importFrom(eq(USER_ID), eq("s0mErAnd0mT0k3nV4lu3ab"), eq(null)))
                .thenThrow(new NoSuchElementException("No share found for that token"));

        mockMvc.perform(post(IMPORT_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }
}
