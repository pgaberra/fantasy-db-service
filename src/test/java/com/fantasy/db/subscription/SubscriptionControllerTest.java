package com.fantasy.db.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String VALID_BODY = """
            {
              "provider": "mock",
              "providerCustomerId": "cus_123",
              "providerSubscriptionId": "sub_123",
              "priceId": "price_123",
              "status": "active",
              "currentPeriodEnd": "2027-01-01T00:00:00Z",
              "cancelAtPeriodEnd": false,
              "eventAt": "2026-08-06T00:00:00Z"
            }
            """;

    private static Subscription subscription(SubscriptionStatus status, Instant currentPeriodEnd) {
        Instant now = Instant.now();
        return new Subscription(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                USER_ID, "mock", "cus_123", "sub_123", "price_123",
                status, currentPeriodEnd, false, now, now, now);
    }

    @Test
    void getReturnsSubscription() throws Exception {
        when(subscriptionService.find(USER_ID))
                .thenReturn(subscription(SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/v1/users/{userId}/subscription", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.providerCustomerId").value("cus_123"));
    }

    @Test
    void getReturns404WhenAbsent() throws Exception {
        when(subscriptionService.find(USER_ID)).thenThrow(new NoSuchElementException("none"));

        mockMvc.perform(get("/api/v1/users/{userId}/subscription", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void putUpsertsAndReturns200() throws Exception {
        when(subscriptionService.upsert(eq(USER_ID), any()))
                .thenReturn(subscription(SubscriptionStatus.ACTIVE, Instant.now().plusSeconds(3600)));

        mockMvc.perform(put("/api/v1/users/{userId}/subscription", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.premium").value(true));
    }

    @Test
    void putReturns400OnBlankProvider() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}/subscription", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"mock\"", "\"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putReturns409OnConcurrentCreateRace() throws Exception {
        when(subscriptionService.upsert(eq(USER_ID), any()))
                .thenThrow(new DataIntegrityViolationException("race"));

        mockMvc.perform(put("/api/v1/users/{userId}/subscription", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }
}
