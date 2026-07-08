CREATE TABLE email_verification_tokens (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ux_email_verification_tokens_token_hash ON email_verification_tokens (token_hash);
CREATE INDEX ix_email_verification_tokens_user_id ON email_verification_tokens (user_id);
