package com.fantasy.db.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

@WebMvcTest(PendingCheckoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class PendingCheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PendingCheckoutService pendingCheckoutService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PATH = "/api/v1/users/{userId}/pending-checkout";

    private static final String VALID_BODY = """
            {
              "provider": "paddle",
              "reference": "txn_2",
              "checkoutUrl": "https://slapstat.test/pay?_ptxn=txn_2",
              "replacesReference": "txn_1"
            }
            """;

    private static PendingCheckout checkout(String reference) {
        Instant now = Instant.now();
        return new PendingCheckout(USER_ID, "paddle", reference, "https://slapstat.test/pay?_ptxn=" + reference,
                now, now);
    }

    @Test
    void getReturnsThePendingCheckout() throws Exception {
        when(pendingCheckoutService.find(USER_ID)).thenReturn(checkout("txn_1"));

        mockMvc.perform(get(PATH, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("paddle"))
                .andExpect(jsonPath("$.reference").value("txn_1"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://slapstat.test/pay?_ptxn=txn_1"));
    }

    @Test
    void getReturns404WhenThereIsNone() throws Exception {
        when(pendingCheckoutService.find(USER_ID)).thenThrow(new NoSuchElementException("none"));

        mockMvc.perform(get(PATH, USER_ID)).andExpect(status().isNotFound());
    }

    @Test
    void putStoresTheCheckout() throws Exception {
        when(pendingCheckoutService.replace(eq(USER_ID), any())).thenReturn(checkout("txn_2"));

        mockMvc.perform(put(PATH, USER_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("txn_2"));
    }

    /** The compare-and-set lost: the caller is told to read again, not that something broke. */
    @Test
    void putReturns409WhenTheCheckoutChangedSinceItWasRead() throws Exception {
        when(pendingCheckoutService.replace(eq(USER_ID), any()))
                .thenThrow(new IllegalStateException(PendingCheckoutService.CHANGED_SINCE_READ));

        mockMvc.perform(put(PATH, USER_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void putReturns400WithoutAReference() throws Exception {
        mockMvc.perform(put(PATH, USER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"txn_2\"", "\"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putReturns400ForAnOversizedCheckoutUrl() throws Exception {
        String oversized = "https://slapstat.test/pay?_ptxn=" + "x".repeat(2100);

        mockMvc.perform(put(PATH, USER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("https://slapstat.test/pay?_ptxn=txn_2", oversized)))
                .andExpect(status().isBadRequest());
    }
}
