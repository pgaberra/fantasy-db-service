CREATE TABLE subscriptions (
    id                       UUID         PRIMARY KEY,
    user_id                  UUID         NOT NULL REFERENCES users(id),
    provider                 VARCHAR(32)  NOT NULL,
    provider_customer_id     VARCHAR(255),
    provider_subscription_id VARCHAR(255),
    price_id                 VARCHAR(255),
    status                   VARCHAR(32)  NOT NULL,
    current_period_end       TIMESTAMPTZ,
    cancel_at_period_end     BOOLEAN      NOT NULL DEFAULT FALSE,
    last_event_at            TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX ux_subscriptions_user_id ON subscriptions (user_id);
CREATE UNIQUE INDEX ux_subscriptions_provider_subscription_id ON subscriptions (provider_subscription_id);
CREATE INDEX ix_subscriptions_provider_customer_id ON subscriptions (provider_customer_id);
