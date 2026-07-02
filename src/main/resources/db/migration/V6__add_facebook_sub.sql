-- Support Facebook sign-in, mirroring google_sub (V3): users created via Facebook have
-- no password and carry the Facebook subject id (a stable per-user identifier), uniquely
-- indexed (Postgres allows multiple NULLs, so other users are unaffected). password_hash
-- is already nullable from V3.
ALTER TABLE users ADD COLUMN facebook_sub VARCHAR(255);

CREATE UNIQUE INDEX ux_users_facebook_sub ON users (facebook_sub);
