package com.fantasy.db.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request Spring itself turns away — a required parameter left out, one that breaks its
 * constraint, one that does not convert to its type, a method the path does not serve — is the
 * caller's mistake and answers with that 4xx. The catch-all used to take these for faults: 500,
 * and an ERROR log that reaches Sentry.
 *
 * <p>The full context, not a slice or a standalone MockMvc: a class-level {@code @Validated}
 * controller is only validated through its AOP proxy, so nothing less reproduces that case.
 * No production endpoint here constrains a request parameter, so the {@code @Size} cases go to
 * two probe controllers that exist only in this test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({SpringClientErrorTest.AopValidatedProbe.class, SpringClientErrorTest.BuiltInValidatedProbe.class})
class SpringClientErrorTest {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    @Autowired
    private MockMvc mockMvc;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger handlerLog = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @BeforeEach
    void captureHandlerLog() {
        logged.start();
        handlerLog.addAppender(logged);
    }

    @AfterEach
    void releaseHandlerLog() {
        handlerLog.detachAppender(logged);
    }

    @Test
    void missingRequiredParameter_isBadRequest() throws Exception {
        expectErrorDto(mockMvc.perform(get("/api/v1/users").header(API_KEY_HEADER, internalApiKey)), 400)
                .andExpect(jsonPath("$.error").value("Bad Request"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void oversizedParameterOnAClassLevelValidatedController_isBadRequest() throws Exception {
        expectErrorDto(mockMvc.perform(get("/api/test-probe/aop-validated")
                        .param("value", "far-too-long-a-value")
                        .header(API_KEY_HEADER, internalApiKey)), 400)
                .andExpect(jsonPath("$.message", containsString("value")))
                .andExpect(jsonPath("$.message", not(containsString("far-too-long-a-value"))));

        assertNothingLoggedAsAnError();
    }

    @Test
    void oversizedParameterOnAControllerWithoutClassLevelValidated_isBadRequest() throws Exception {
        expectErrorDto(mockMvc.perform(get("/api/test-probe/built-in-validated")
                        .param("value", "far-too-long-a-value")
                        .header(API_KEY_HEADER, internalApiKey)), 400)
                .andExpect(jsonPath("$.message", not(containsString("far-too-long-a-value"))));

        assertNothingLoggedAsAnError();
    }

    @Test
    void pathVariableThatIsNotAUuid_isBadRequest() throws Exception {
        expectErrorDto(mockMvc.perform(get("/api/v1/users/not-a-uuid").header(API_KEY_HEADER, internalApiKey)), 400)
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'userId'"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void methodThePathDoesNotServe_isMethodNotAllowedAndKeepsAllow() throws Exception {
        expectErrorDto(mockMvc.perform(delete("/api/v1/users").header(API_KEY_HEADER, internalApiKey)), 405)
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.message").value("Method Not Allowed"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void acceptTheEndpointCannotMeet_isNotAcceptable() throws Exception {
        expectErrorDto(mockMvc.perform(get("/api/v1/users/exists")
                        .param("email", "nobody@example.com")
                        .accept(MediaType.IMAGE_PNG)
                        .header(API_KEY_HEADER, internalApiKey)), 406)
                .andExpect(jsonPath("$.message").value("Not Acceptable"));

        assertNothingLoggedAsAnError();
    }

    private static ResultActions expectErrorDto(ResultActions result, int status) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private void assertNothingLoggedAsAnError() {
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .isEmpty();
    }

    /** Validated by the method-validation AOP proxy: a violation is a {@code ConstraintViolationException}. */
    @RestController
    @Validated
    static class AopValidatedProbe {
        @GetMapping("/api/test-probe/aop-validated")
        public String probe(@RequestParam @Size(max = 8) String value) {
            return value;
        }
    }

    /** Validated by Spring MVC itself: a violation is a {@code HandlerMethodValidationException}. */
    @RestController
    static class BuiltInValidatedProbe {
        @GetMapping("/api/test-probe/built-in-validated")
        public String probe(@RequestParam @Size(max = 8) String value) {
            return value;
        }
    }
}
