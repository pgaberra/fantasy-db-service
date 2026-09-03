-- The account's profile picture, in its own table rather than as a column on users: every
-- sign-in reads the user row, and none of those reads want a picture riding along with it.
--
-- The bytes are stored as they arrive from the BFF, which has already scaled the image down to
-- the size the app draws and checked that it really is a PNG, JPEG or WebP.
CREATE TABLE user_avatars (
    user_id      UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content_type VARCHAR(32) NOT NULL,
    data         BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);
