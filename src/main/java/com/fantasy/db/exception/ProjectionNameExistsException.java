package com.fantasy.db.exception;

public class ProjectionNameExistsException extends RuntimeException {
    public ProjectionNameExistsException(String name) {
        super("A projection already exists with name: " + name);
    }
}
