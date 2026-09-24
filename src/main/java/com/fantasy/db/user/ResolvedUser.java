package com.fantasy.db.user;

public record ResolvedUser(User user, boolean created) {

    static ResolvedUser existing(User user) {
        return new ResolvedUser(user, false);
    }

    static ResolvedUser created(User user) {
        return new ResolvedUser(user, true);
    }
}
