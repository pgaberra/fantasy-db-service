-- A public name for an account, and the end of the per-share alias it replaces.
--
-- Nullable: an account only needs a name once it publishes something, so nobody is forced to
-- pick one at sign-up. Sharing is where it becomes required (enforced in the share service).
ALTER TABLE users ADD COLUMN username VARCHAR(20);

-- Unique regardless of case: "Alex" and "alex" are the same name to a reader, so they must not
-- both exist. A functional index rather than a plain UNIQUE for exactly that reason.
CREATE UNIQUE INDEX ux_users_username_lower ON users (LOWER(username));

-- The share alias existed only because accounts had no name of their own.
ALTER TABLE projection_shares DROP COLUMN author_alias;
