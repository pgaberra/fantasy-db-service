package com.fantasy.db.user;

import com.fantasy.db.exception.EmailAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
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
    void createReturns409OnDuplicate() throws Exception {
        when(userService.create(eq("ivan@example.com"), any()))
                .thenThrow(new EmailAlreadyExistsException("ivan@example.com"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ivan@example.com\",\"passwordHash\":\"hash\"}"))
                .andExpect(status().isConflict());
    }
}
