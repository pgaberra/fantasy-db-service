package com.fantasy.db.exception;

import java.util.UUID;

public class ProjectionNotFoundException extends RuntimeException {
    public ProjectionNotFoundException(UUID id) {
        super("No projection found with id: " + id);
    }
}
