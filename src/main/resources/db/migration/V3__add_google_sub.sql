-- Support Google sign-in: users created via Google have no password, and carry the
-- Google subject id (stable per-user identifier). password_hash becomes nullable, and
-- google_sub is uniquely indexed (Postgres allows multiple NULLs, so password-only
-- users are unaffected).
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN google_sub VARCHAR(255);

CREATE UNIQUE INDEX ux_users_google_sub ON users (google_sub);
