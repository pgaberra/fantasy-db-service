package com.fantasy.db.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A caller that goes away mid-exchange is not a fault in this service, and must not be reported
 * as one — ERROR-level logs are what reach Sentry. Both occurrences on record were exactly that:
 * a projection response cut off 462 players in with a broken pipe, and an update body that
 * stopped arriving part-way through. Neither is actionable and neither can even be answered.
 *
 * <p>The other half of these tests matters more: everything that is <em>not</em> a proven abort
 * has to keep the ERROR it has today. A response we cannot serialize and a body we cannot parse
 * are real bugs, and this change must not be a way to stop hearing about them.
 */
class GlobalExceptionHandlerClientAbortTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // --- the write path: the response could not be delivered -----------------------------------

    @Test
    void brokenPipeMidResponse_answersWithNothingAtAll() {
        ResponseEntity<ErrorDto> response = handler.handleUnwritableResponse(
                unwritableResponse(new ClientAbortException(new IOException("Broken pipe"))),
                request());

        assertThat(response).isNull();
    }

    /**
     * The shape actually recorded in JAVA-SPRING-BOOT-18: Jackson wrapped the abort several
     * layers deep before it surfaced as a write failure.
     */
    @Test
    void abortNestedDeeperInTheCauseChain_isStillRecognised() {
        Throwable nested = new IllegalStateException("wrapped",
                new ClientAbortException(new IOException("Broken pipe")));

        assertThat(handler.handleUnwritableResponse(unwritableResponse(nested), request())).isNull();
    }

    @Test
    void responseAlreadyUnusable_countsAsAnAbortWithoutTheOriginalIoFailure() {
        AsyncRequestNotUsableException unusable =
                new AsyncRequestNotUsableException("Response not usable after response errors.");

        assertThat(handler.handleUnwritableResponse(unusable, request())).isNull();
    }

    @Test
    void serialisationFailure_isStillAnInternalError() {
        ResponseEntity<ErrorDto> response = handler.handleUnwritableResponse(
                unwritableResponse(new IllegalArgumentException("No serializer found")),
                request());

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- the read path: the request body stopped arriving ---------------------------------------

    @Test
    void clientAbortMidBody_isReportedAsAnIncompleteRequest() {
        ResponseEntity<ErrorDto> response = handler.handleUnreadableBody(
                unreadableBody(new ClientAbortException(new EOFException())), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("The request body was not received in full");
    }

    /**
     * Malformed JSON from a caller that is one of our own services is a bug on our side of the
     * wire, so it keeps the ERROR and the 500 it has today rather than being downgraded with the
     * aborts.
     */
    @Test
    void malformedJson_keepsTheErrorItHasToday() {
        ResponseEntity<ErrorDto> response = handler.handleUnreadableBody(
                unreadableBody(new IllegalArgumentException("Unexpected character")), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * The narrowing that separates this handler from the one in fantasy-bff. An EOF with no
     * {@link ClientAbortException} above it is not proof that the peer ended the exchange, and
     * an unproven abort must stay loud.
     */
    @Test
    void bareEndOfInput_isNotProofOfAnAbortAndStaysLoud() {
        ResponseEntity<ErrorDto> response =
                handler.handleUnreadableBody(unreadableBody(new EOFException()), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void bareEndOfInputOnTheWritePath_alsoStaysLoud() {
        ResponseEntity<ErrorDto> response =
                handler.handleUnwritableResponse(unwritableResponse(new EOFException()), request());

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** A cause chain that points at itself must not spin the walk forever. */
    @Test
    void selfReferencingCauseChain_terminates() {
        assertThat(handler.handleUnwritableResponse(new SelfCausedException(), request())).isNotNull();
    }

    private static HttpMessageNotWritableException unwritableResponse(Throwable cause) {
        return new HttpMessageNotWritableException("Could not write JSON", cause);
    }

    private static HttpMessageNotReadableException unreadableBody(Throwable cause) {
        return new HttpMessageNotReadableException("JSON parse error", cause, emptyInputMessage());
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/api/v1/users/abc/projections/def");
        when(request.getContentLengthLong()).thenReturn(520_000L);
        return request;
    }

    private static HttpInputMessage emptyInputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return InputStream.nullInputStream();
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }
        };
    }

    private static final class SelfCausedException extends RuntimeException {

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
