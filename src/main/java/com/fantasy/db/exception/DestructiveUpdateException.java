package com.fantasy.db.exception;

/**
 * A well-formed request that would destroy stored data the caller almost certainly did not mean
 * to destroy. Unlike the other 4xx outcomes it is not an expected client outcome but a client
 * bug, so {@link GlobalExceptionHandler} logs it at ERROR and it is worth alerting on.
 */
public class DestructiveUpdateException extends RuntimeException {

    public DestructiveUpdateException(String message) {
        super(message);
    }
}
