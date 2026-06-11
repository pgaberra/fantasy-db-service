CREATE TABLE user_projections (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    data       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    UNIQUE (user_id, name)
);

CREATE INDEX idx_user_projections_user_id ON user_projections (user_id);
