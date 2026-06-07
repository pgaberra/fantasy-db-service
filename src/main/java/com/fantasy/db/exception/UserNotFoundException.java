package com.fantasy.db.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("No user found for email: " + email);
    }
}
