-- Session-invalidation counter. Bumped whenever every existing session for a user must stop
-- working (e.g. a password reset). The BFF stamps this value into the refresh token and rejects
-- a refresh whose token_version is older than the user's current one.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
