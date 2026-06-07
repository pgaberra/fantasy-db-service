package com.fantasy.db.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("A user already exists for email: " + email);
    }
}
