package com.fantasy.db.user.dto;

import com.fantasy.db.user.User;

public record UserResponse(
        String id,
        String email,
        String passwordHash
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId().toString(), user.getEmail(), user.getPasswordHash());
    }
}
