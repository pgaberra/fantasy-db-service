package com.fantasy.db.premium;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPremiumController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserPremiumControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PremiumService premiumService;

    private static String body(String expiresAt) {
        return """
                {
                  "expiresAt": "%s",
                  "grantedBy": "admin@example.com",
                  "reason": "a friend"
                }
                """.formatted(expiresAt);
    }

    @Test
    void grantReturns201() throws Exception {
        Instant expiry = Instant.now().plus(60, ChronoUnit.DAYS);
        when(premiumService.grant(eq(USER_ID), any())).thenReturn(
                PremiumGrant.create(USER_ID, "admin@example.com", "a friend", expiry));

        mockMvc.perform(post("/api/v1/users/{userId}/premium/grants", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(expiry.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.grantedBy").value("admin@example.com"));
    }

    @Test
    void grantForAnUnknownUserReturns404() throws Exception {
        when(premiumService.grant(eq(USER_ID), any())).thenThrow(new NoSuchElementException("No user"));

        mockMvc.perform(post("/api/v1/users/{userId}/premium/grants", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Instant.now().plus(60, ChronoUnit.DAYS).toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void grantWithAPastExpiryReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/premium/grants", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Instant.now().minus(1, ChronoUnit.DAYS).toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokeReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}/premium/grants", USER_ID))
                .andExpect(status().isNoContent());

        verify(premiumService).revokeActiveGrants(USER_ID);
    }
}
