package com.fantasy.db.projection;

import com.fantasy.db.exception.ProjectionNameExistsException;
import com.fantasy.db.exception.ProjectionNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
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

    private UserProjection projection(String name, String data) {
        return new UserProjection(PROJECTION_ID, USER_ID, name, data, Instant.now(), Instant.now());
    }

    @Test
    void listReturnsSummaries() throws Exception {
        when(userProjectionService.findAll(USER_ID)).thenReturn(List.of(projection("My league", "{}")));

        mockMvc.perform(get("/api/v1/users/{userId}/projections", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My league"))
                .andExpect(jsonPath("$[0].id").value(PROJECTION_ID.toString()));
    }

    @Test
    void getReturnsProjectionWithData() throws Exception {
        when(userProjectionService.findById(USER_ID, PROJECTION_ID))
                .thenReturn(projection("My league", "{\"x\":1}"));

        mockMvc.perform(get("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My league"))
                .andExpect(jsonPath("$.data").value("{\"x\":1}"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(userProjectionService.findById(USER_ID, PROJECTION_ID))
                .thenThrow(new ProjectionNotFoundException(PROJECTION_ID));

        mockMvc.perform(get("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("My league"), any()))
                .thenReturn(projection("My league", "{}"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My league\",\"data\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("My league"));
    }

    @Test
    void createReturns400OnBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"data\":\"{}\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409OnDuplicateName() throws Exception {
        when(userProjectionService.create(eq(USER_ID), eq("Dup"), any()))
                .thenThrow(new ProjectionNameExistsException("Dup"));

        mockMvc.perform(post("/api/v1/users/{userId}/projections", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dup\",\"data\":\"{}\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}/projections/{id}", USER_ID, PROJECTION_ID))
                .andExpect(status().isNoContent());
    }
}
