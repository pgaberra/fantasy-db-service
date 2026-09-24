package com.fantasy.db.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getByEmailReturnsUser() throws Exception {
        User user = new User(UUID.randomUUID(), "frank@example.com", "hash", Instant.now());
        when(userService.findByEmail("frank@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/users").param("email", "frank@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("frank@example.com"))
                .andExpect(jsonPath("$.passwordHash").value("hash"))
                .andExpect(jsonPath("$.id").value(user.getId().toString()));
    }

    @Test
    void getByEmailReturns404WhenMissing() throws Exception {
        when(userService.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users").param("email", "missing@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void existsReturnsBoolean() throws Exception {
        when(userService.existsByEmail("grace@example.com")).thenReturn(true);

        mockMvc.perform(get("/api/v1/users/exists").param("email", "grace@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void createReturns201() throws Exception {
        User user = new User(UUID.randomUUID(), "heidi@example.com", "hash", Instant.now());
        when(userService.create(eq("heidi@example.com"), any())).thenReturn(user);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"heidi@example.com\",\"passwordHash\":\"hash\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("heidi@example.com"));
    }

    @Test
    void createReturns400OnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"passwordHash\":\"hash\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns400OnOversizedPasswordHash() throws Exception {
        String oversized = "a".repeat(101);
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@example.com\",\"passwordHash\":\"" + oversized + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReturns409OnDuplicate() throws Exception {
        when(userService.create(eq("ivan@example.com"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ivan@example.com\",\"passwordHash\":\"hash\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void googleEndpointReturnsResolvedUser() throws Exception {
        User user = User.createWithGoogle("gabe@example.com", "google-99");
        when(userService.findOrCreateGoogleUser(eq("gabe@example.com"), eq("google-99")))
                .thenReturn(new ResolvedUser(user, false));

        mockMvc.perform(post("/api/v1/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gabe@example.com\",\"googleSub\":\"google-99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gabe@example.com"))
                .andExpect(jsonPath("$.googleSub").value("google-99"));
    }

    @Test
    void googleEndpointReturns201WhenItCreatedTheUser() throws Exception {
        User user = User.createWithGoogle("gwen@example.com", "google-100");
        when(userService.findOrCreateGoogleUser(eq("gwen@example.com"), eq("google-100")))
                .thenReturn(new ResolvedUser(user, true));

        mockMvc.perform(post("/api/v1/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gwen@example.com\",\"googleSub\":\"google-100\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.googleSub").value("google-100"));
    }

    @Test
    void googleEndpointReturns400OnBlankSubject() throws Exception {
        mockMvc.perform(post("/api/v1/users/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gabe@example.com\",\"googleSub\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facebookEndpointReturnsResolvedUser() throws Exception {
        User user = User.createWithFacebook("faye@example.com", "facebook-99");
        when(userService.findOrCreateFacebookUser(eq("faye@example.com"), eq("facebook-99")))
                .thenReturn(new ResolvedUser(user, false));

        mockMvc.perform(post("/api/v1/users/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"faye@example.com\",\"facebookSub\":\"facebook-99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("faye@example.com"))
                .andExpect(jsonPath("$.facebookSub").value("facebook-99"));
    }

    @Test
    void facebookEndpointReturns201WhenItCreatedTheUser() throws Exception {
        User user = User.createWithFacebook("finn@example.com", "facebook-100");
        when(userService.findOrCreateFacebookUser(eq("finn@example.com"), eq("facebook-100")))
                .thenReturn(new ResolvedUser(user, true));

        mockMvc.perform(post("/api/v1/users/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"finn@example.com\",\"facebookSub\":\"facebook-100\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facebookSub").value("facebook-100"));
    }

    @Test
    void facebookEndpointReturns400OnBlankSubject() throws Exception {
        mockMvc.perform(post("/api/v1/users/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"faye@example.com\",\"facebookSub\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returnsTheUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "alex@example.com", "hash", java.time.Instant.now());
        when(userService.findById(userId)).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alex@example.com"));
    }

    @Test
    void getById_returnsNotFoundForAnUnknownUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.findById(userId)).thenThrow(new NoSuchElementException("No user"));

        mockMvc.perform(get("/api/v1/users/{userId}", userId)).andExpect(status().isNotFound());
    }

    @Test
    void revokeSessionsReturns204() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/users/{userId}/sessions/revoke", userId))
                .andExpect(status().isNoContent());

        verify(userService).revokeSessions(userId);
    }

    @Test
    void revokeSessionsReturns404ForUnknownUser() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new NoSuchElementException("No user"))
                .when(userService).revokeSessions(userId);

        mockMvc.perform(post("/api/v1/users/{userId}/sessions/revoke", userId))
                .andExpect(status().isNotFound());
    }
}
