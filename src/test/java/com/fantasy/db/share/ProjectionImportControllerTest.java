package com.fantasy.db.share;

import com.fantasy.db.projection.PlayerIdSpace;
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
import java.util.ConcurrentModificationException;
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
    private static final String TOKEN = "s0mErAnd0mT0k3nV4lu3ab";
    private static final String FOLLOW_PATH = "/api/v1/users/" + USER_ID + "/projections/imports";
    private static final String COPY_PATH = "/api/v1/users/" + USER_ID + "/projections/copies";

    private static final String VALID_BODY = """
            { "token": "s0mErAnd0mT0k3nV4lu3ab" }
            """;

    private static ProjectionData data() {
        return new ProjectionData(
                new ProjectionSettings(ScoringType.POINTS, Map.of("goals", 4.5), List.of("goals"),
                        List.of("gp"), Map.of(), Map.of("goals", 0), true, 12, null, null, null, null,
                        null, null, null, null, null),
                List.of(new PlayerProjection(1, PlayerType.SKATER,
                        new PlayerStats(Map.of("gp", 82.0), Map.of("goals", 64.0)))),
                null,
                null);
    }

    private static UserProjection followed() {
        return new UserProjection(PROJECTION_ID, USER_ID, "My league", ProjectionKind.IMPORTED,
                null, Season.SEASON_2026_2027, data(), TOKEN, "alex",
                Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-01T10:00:00Z"));
    }

    private static UserProjection copied() {
        return UserProjection.create(USER_ID, "Copy of My league", ProjectionKind.PROJECTION, null,
                Season.SEASON_2026_2027, data(), PlayerIdSpace.YAHOO);
    }

    @Test
    void followsASharedBoard() throws Exception {
        when(projectionImportService.follow(eq(USER_ID), eq(TOKEN), eq(null)))
                .thenReturn(new ProjectionImportService.Follow(followed(), true));

        mockMvc.perform(post(FOLLOW_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("imported"))
                .andExpect(jsonPath("$.origin.shareToken").value(TOKEN))
                .andExpect(jsonPath("$.origin.authorUsername").value("alex"));
    }

    /** Following a link already followed creates nothing, and says so with 200 rather than 201. */
    @Test
    void answersALinkAlreadyFollowedWithTheFollowItHas() throws Exception {
        when(projectionImportService.follow(eq(USER_ID), eq(TOKEN), eq(null)))
                .thenReturn(new ProjectionImportService.Follow(followed(), false));

        mockMvc.perform(post(FOLLOW_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROJECTION_ID.toString()));
    }

    @Test
    void rejectsABlankToken() throws Exception {
        mockMvc.perform(post(FOLLOW_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \" \" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownTokenAsNotFound() throws Exception {
        when(projectionImportService.follow(eq(USER_ID), eq(TOKEN), eq(null)))
                .thenThrow(new NoSuchElementException("No share found for that token"));

        mockMvc.perform(post(FOLLOW_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    /** Your own link is a bad request, not a conflict: there is nothing to resolve by retrying. */
    @Test
    void reportsTheCallersOwnLinkAsABadRequest() throws Exception {
        when(projectionImportService.follow(eq(USER_ID), eq(TOKEN), eq(null)))
                .thenThrow(new IllegalArgumentException("This link is your own board"));

        mockMvc.perform(post(FOLLOW_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passesOnTheStampTheReaderSaw() throws Exception {
        when(projectionImportService.follow(eq(USER_ID), eq(TOKEN),
                eq(Instant.parse("2026-09-18T08:00:00.123456Z"))))
                .thenReturn(new ProjectionImportService.Follow(followed(), true));

        mockMvc.perform(post(FOLLOW_PATH).contentType(MediaType.APPLICATION_JSON).content(
                        "{ \"token\": \"" + TOKEN + "\", "
                                + "\"seenUpdatedAt\": \"2026-09-18T08:00:00.123456Z\" }"))
                .andExpect(status().isCreated());
    }

    /** A stale read is its own status, so the BFF and the page can tell it from anything else. */
    @Test
    void reportsABoardChangedSinceItWasReadAsPreconditionFailed() throws Exception {
        when(projectionImportService.follow(eq(USER_ID), eq(TOKEN), eq(null)))
                .thenThrow(new ConcurrentModificationException("changed"));

        mockMvc.perform(post(FOLLOW_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void copiesASharedBoard() throws Exception {
        when(projectionImportService.copy(eq(USER_ID), eq(TOKEN), eq(null))).thenReturn(copied());

        mockMvc.perform(post(COPY_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("projection"))
                .andExpect(jsonPath("$.name").value("Copy of My league"))
                .andExpect(jsonPath("$.origin").doesNotExist());
    }

    @Test
    void rejectsACopyWithABlankToken() throws Exception {
        mockMvc.perform(post(COPY_PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"token\": \" \" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAnUnknownTokenOnCopyAsNotFound() throws Exception {
        when(projectionImportService.copy(eq(USER_ID), eq(TOKEN), eq(null)))
                .thenThrow(new NoSuchElementException("No share found for that token"));

        mockMvc.perform(post(COPY_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportsACopyOfABoardChangedSinceItWasReadAsPreconditionFailed() throws Exception {
        when(projectionImportService.copy(eq(USER_ID), eq(TOKEN), eq(null)))
                .thenThrow(new ConcurrentModificationException("changed"));

        mockMvc.perform(post(COPY_PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isPreconditionFailed());
    }
}
