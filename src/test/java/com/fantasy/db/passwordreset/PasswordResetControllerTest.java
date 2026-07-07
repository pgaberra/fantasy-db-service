package com.fantasy.db.passwordreset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    void issueReturns200WithToken() throws Exception {
        when(passwordResetService.issueToken("amy@example.com"))
                .thenReturn(Optional.of(new PasswordResetService.IssuedToken(
                        "raw-token", Instant.parse("2026-06-17T12:00:00Z"))));

        mockMvc.perform(post("/api/v1/users/password-reset/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"amy@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("raw-token"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void issueReturns204WhenNoResettableAccount() throws Exception {
        when(passwordResetService.issueToken("google@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/users/password-reset/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"google@example.com\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void issueReturns400OnInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetReturns204OnSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\",\"passwordHash\":\"new-hash\"}"))
                .andExpect(status().isNoContent());

        verify(passwordResetService).resetPassword("raw-token", "new-hash");
    }

    @Test
    void resetReturns404OnInvalidToken() throws Exception {
        doThrow(new NoSuchElementException("Invalid or expired password reset token"))
                .when(passwordResetService).resetPassword(eq("bad-token"), any());

        mockMvc.perform(post("/api/v1/users/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad-token\",\"passwordHash\":\"new-hash\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetReturns400OnBlankToken() throws Exception {
        mockMvc.perform(post("/api/v1/users/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\",\"passwordHash\":\"new-hash\"}"))
                .andExpect(status().isBadRequest());
    }
}
