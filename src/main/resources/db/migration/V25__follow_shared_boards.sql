-- An imported board stamped with the link it came from is now a *follow* of that link: it holds
-- the board the share holds, is renamed with it, disappears with it, and its owner may change
-- nothing on it but their own draft. What was a frozen copy taken at one moment becomes the
-- author's board seen from another account, and a reader who wants a board of their own takes a
-- copy instead (POST /projections/copies), which is stamped with nothing.
--
-- So the rows already in the table have to be made into what a follow now is, before the
-- constraints that say so can be created over them.

-- Nothing but an import has ever been stamped with a token, but the foreign key below is on the
-- column rather than on the kind, so anything else holding one would be a follow the code cannot
-- explain. Cleared rather than kept.
UPDATE user_projections
SET origin_share_token = NULL,
    origin_author_username = NULL
WHERE kind <> 'IMPORTED'
  AND origin_share_token IS NOT NULL;

-- A copy whose share is gone has nothing left to follow, and the product owner's call is that it
-- goes with it — the rows in it are the author's numbers as they were, without the board they
-- belonged to and without the author's later corrections.
--
-- Looping because deleting one can strand another: a follower who shared their own copy has a
-- share of their own, that share cascades away with the row, and whoever followed *that* link is
-- then stranded in turn. Bounded rather than plain `LOOP ... EXIT WHEN`, so a cycle that will not
-- converge fails the migration instead of spinning while it holds a lock.
DO $$
DECLARE
    removed  INTEGER;
    attempts INTEGER := 0;
BEGIN
    LOOP
        DELETE FROM user_projections p
        WHERE p.origin_share_token IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM projection_shares s WHERE s.token = p.origin_share_token);

        GET DIAGNOSTICS removed = ROW_COUNT;
        attempts := attempts + 1;
        EXIT WHEN removed = 0 OR attempts >= 10;
    END LOOP;
END $$;

-- Importing your own link is refused now (the board is already in the account, and a follow of it
-- would mirror a projection into itself), and staging has two rows that did exactly that. They
-- are somebody's work, so they become an ordinary projection rather than being deleted. They keep
-- the names they hold, which are already unique among the user's own.
UPDATE user_projections p
SET kind = 'PROJECTION',
    origin_share_token = NULL,
    origin_author_username = NULL
FROM projection_shares s
WHERE s.token = p.origin_share_token
  AND s.user_id = p.user_id;

-- One follow per (user, link) from here on. Where a user imported the same link more than once,
-- the oldest stays the follow and the rest become projections of their own: two rows mirroring
-- one link would be two copies of one board, both renamed under the author, neither told apart in
-- the list. Nothing is deleted — a later copy may hold a draft the first does not.
UPDATE user_projections p
SET kind = 'PROJECTION',
    origin_share_token = NULL,
    origin_author_username = NULL
FROM (
    SELECT id,
           row_number() OVER (PARTITION BY user_id, origin_share_token
                              ORDER BY created_at, id) AS n
    FROM user_projections
    WHERE origin_share_token IS NOT NULL
) ranked
WHERE p.id = ranked.id
  AND ranked.n > 1;

-- Dropped before the follows are renamed after their shares: a follow leaving the name namespace
-- can take a name one of the user's own boards holds, and under the old index that rename would
-- fail. The narrowed index is created at the end of this migration.
DROP INDEX uk_user_projections_user_name;

-- What is left mirrors its share: the published name, and the published board stripped back to
-- what a projection stores (the identity and rank a shared row carries belong to the page that
-- renders it). The follower's own draft stays, and the id space comes from the share, which is
-- where the rows come from. The stamp moves only for a row this actually changes.
WITH mirrored AS (
    SELECT p.id,
           s.name,
           jsonb_build_object(
               'settings', s.data -> 'settings',
               'players', COALESCE((
                   SELECT jsonb_agg(jsonb_build_object(
                              'playerId', elem -> 'playerId',
                              'type', elem -> 'type',
                              'stats', elem -> 'stats') ORDER BY ord)
                   FROM jsonb_array_elements(s.data -> 'players') WITH ORDINALITY AS t(elem, ord)
               ), '[]'::jsonb),
               'draft', p.data -> 'draft',
               'positionOverrides', s.data -> 'positionOverrides') AS data,
           s.player_id_space
    FROM user_projections p
    JOIN projection_shares s ON s.token = p.origin_share_token
)
UPDATE user_projections p
SET name = m.name,
    data = m.data,
    player_id_space = m.player_id_space,
    updated_at = now()
FROM mirrored m
WHERE p.id = m.id
  AND (p.name <> m.name
       OR p.data IS DISTINCT FROM m.data
       OR p.player_id_space <> m.player_id_space);

-- A follow exists only as long as the link does. Unsharing is deleting the projection behind the
-- link, which already cascades the share away; this carries that through to the follows. The
-- token a share is created with is never rewritten (no endpoint touches it, and the entity maps
-- it as non-updatable), so no ON UPDATE rule is needed to go with it.
ALTER TABLE user_projections
    ADD CONSTRAINT fk_user_projections_origin_share
    FOREIGN KEY (origin_share_token) REFERENCES projection_shares (token) ON DELETE CASCADE;

-- Only an import is ever a follow. A spreadsheet import is IMPORTED with no token and stays the
-- user's own work, which is why the rule is written on the column rather than on the kind.
ALTER TABLE user_projections
    ADD CONSTRAINT ck_user_projections_origin_is_imported
    CHECK (origin_share_token IS NULL OR kind = 'IMPORTED');

-- One follow per user and link, and the index a publish uses to find every follow of a token, in
-- one: the token leads so a lookup by token alone can use it.
CREATE UNIQUE INDEX uk_user_projections_origin_share_user
    ON user_projections (origin_share_token, user_id)
    WHERE origin_share_token IS NOT NULL;

-- V19's namespace, minus the follows. A follow is named by its author and renamed whenever they
-- rename their board, so holding it to the names the follower has chosen would let a stranger's
-- rename collide with their work — and the follower cannot rename their way out of it, since a
-- follow takes nothing from an update but its draft.
CREATE UNIQUE INDEX uk_user_projections_user_name
    ON user_projections (user_id, name)
    WHERE kind <> 'PRESET_DRAFT' AND origin_share_token IS NULL;
