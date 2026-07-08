-- Whether the account's email address has been proven. Password sign-ups start unverified and
-- must consume an email-verification token; social sign-ups are verified by the provider.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Grandfather every existing account as verified: they predate email verification, so we must not
-- retroactively lock them out or nag them. New rows get their value from the application.
UPDATE users SET email_verified = TRUE;
