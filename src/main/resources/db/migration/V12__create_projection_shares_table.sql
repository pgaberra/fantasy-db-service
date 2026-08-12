CREATE TABLE projection_shares (
    id            UUID         PRIMARY KEY,
    projection_id UUID         NOT NULL UNIQUE REFERENCES user_projections (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token         VARCHAR(64)  NOT NULL UNIQUE,
    author_alias  VARCHAR(40),
    name          VARCHAR(100) NOT NULL,
    season        VARCHAR(20)  NOT NULL,
    data          JSONB        NOT NULL,
    view_count    BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_projection_shares_user_id ON projection_shares (user_id);
