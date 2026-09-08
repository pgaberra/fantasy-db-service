-- Premium handed out by an admin, kept apart from the subscriptions table so the two can never
-- overwrite each other: a provider webhook owns a subscription row start to finish, and a grant
-- has to survive one arriving (or not arriving) for the same user.
--
-- Rows are kept rather than deleted — revoking sets revoked_at — so who was given what, by whom
-- and why stays readable afterwards.
CREATE TABLE premium_grants (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    granted_by VARCHAR(255) NOT NULL,
    reason     VARCHAR(255),
    starts_at  TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_premium_grants_user_id ON premium_grants (user_id);
CREATE INDEX ix_premium_grants_expires_at ON premium_grants (expires_at) WHERE revoked_at IS NULL;
