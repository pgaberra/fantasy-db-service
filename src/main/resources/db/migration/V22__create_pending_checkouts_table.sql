-- The one checkout a user has open with the payment provider. Keyed by user, so a user can never
-- hold two: a second checkout reuses this one while it is still payable, which is what stops two
-- tabs from each opening, and paying, a checkout of their own.
CREATE TABLE pending_checkouts (
    user_id      UUID          PRIMARY KEY REFERENCES users(id),
    provider     VARCHAR(32)   NOT NULL,
    reference    VARCHAR(255)  NOT NULL,
    checkout_url VARCHAR(2048) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);
