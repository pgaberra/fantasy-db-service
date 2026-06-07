CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

-- Case-insensitive uniqueness on email.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
