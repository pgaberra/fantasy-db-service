package com.fantasy.db.exception;

import com.fantasy.db.config.RequestBodyByteCountFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // The 4xx handlers below are expected client outcomes, not server faults, so they
    // are intentionally not logged. Anything unexpected falls to handleUnexpected, which
    // logs the full stack trace — an unmatched exception must never be silently swallowed.

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorDto> handleNoSuchElement(NoSuchElementException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDataIntegrity(DataIntegrityViolationException e) {
        return build(HttpStatus.CONFLICT, "The resource conflicts with an existing one");
    }

    /**
     * A precondition on the caller's own data is not met — sharing a projection before the account
     * has a username, for instance. The request is well formed, so this is a conflict with the
     * current state rather than a bad request.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDto> handleIllegalState(IllegalStateException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * The request is malformed in a way Bean Validation cannot express — a crosswalk that maps
     * one player id two different ways, an unknown enum code. It reached here as a 500 before,
     * which told the caller nothing about a mistake that is entirely theirs to fix.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgument(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * A request for a path this service does not serve. Without this the catch-all turns it into a
     * 500 with a full stack trace. Every caller here is one of our own services, so the way it
     * happens is version skew: a BFF newer than the deployed copy of this service calling an
     * endpoint that build has never heard of, which is what espn-service logged in production.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "No resource found for the requested path");
    }

    /**
     * The response could not be written. The one cause worth separating out is the caller going
     * away mid-response: the BFF gave up on a slow read, or the connection died, and the write
     * failed with a broken pipe. Nothing is wrong here, and nothing can be sent either — the
     * connection an error would travel over is the one that just died. So: WARN, and no response.
     *
     * <p>Anything else keeps exactly what it had before — {@link #handleUnexpected} logs it at
     * ERROR and answers 500. A write failure that is not an abort is a value we cannot serialize,
     * which is a real fault in this service and must stay loud.
     */
    @ExceptionHandler({HttpMessageNotWritableException.class, AsyncRequestNotUsableException.class,
            ClientAbortException.class})
    public ResponseEntity<ErrorDto> handleUnwritableResponse(Exception e, HttpServletRequest request) {
        if (!isClientAbort(e)) {
            return handleUnexpected(e);
        }
        log.warn("Caller went away before the response was written: {} {}",
                safe(request.getMethod()), safe(request.getRequestURI()));
        return null;
    }

    /**
     * The request body could not be read. Again only the abort is separated out — the body stopped
     * arriving mid-stream — and it is logged with how much of it got here against what was
     * declared. That is the measurement which distinguishes a body cut at a fixed size (a proxy
     * cap) from one that simply stopped, and it is what keeps this downgrade honest: we stop
     * alerting on the transport, not on a caller that is truncating its own writes.
     *
     * <p>A body that is malformed rather than incomplete falls through to
     * {@link #handleUnexpected} unchanged. Every caller of this service is one of our own
     * services, so JSON we cannot parse is a bug on our side of the wire and keeps its alert.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleUnreadableBody(HttpMessageNotReadableException e,
                                                         HttpServletRequest request) {
        if (!isClientAbort(e)) {
            return handleUnexpected(e);
        }
        log.warn("Request body stopped arriving: {} {} - read {} of {} declared bytes",
                safe(request.getMethod()), safe(request.getRequestURI()),
                RequestBodyByteCountFilter.bytesRead(request), request.getContentLengthLong());
        return build(HttpStatus.BAD_REQUEST, "The request body was not received in full");
    }

    /**
     * Line breaks out of a value the caller controls, so a crafted path cannot forge log records
     * around the one this writes. Same treatment the downstream-response body already gets in the
     * BFF's handler.
     */
    private static String safe(String requestValue) {
        return requestValue == null ? "" : requestValue.replace('\r', '_').replace('\n', '_');
    }

    /**
     * Deliberately narrower than the same check in fantasy-bff, which also counts a bare
     * {@link java.io.EOFException} as an abort. Both aborts ever recorded against this service
     * carried a {@link ClientAbortException} — Tomcat's own marker that the peer, not this
     * process, ended the exchange — so requiring one costs nothing here and rules out silencing
     * an EOF that came from somewhere other than the socket.
     *
     * <p>{@link AsyncRequestNotUsableException} counts in its own right: Spring raises it once the
     * response has already failed, so the connection is known to be unusable even when the
     * original {@code IOException} is no longer in the chain.
     */
    private static boolean isClientAbort(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ClientAbortException
                    || cause instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (cause.getCause() == cause) {
                return false;
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorDto> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ErrorDto.of(status.value(), status.getReasonPhrase(), message));
    }
}
