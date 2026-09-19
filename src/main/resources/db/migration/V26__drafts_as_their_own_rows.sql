-- A draft becomes a row of its own.
--
-- It used to be a field on the board it was drafted against (`data -> 'draft'`), which made
-- "one draft per board" a property of the storage rather than a decision anyone took: a second
-- draft against a projection had nowhere to go. A preset draft was already a row, because a
-- preset has no board behind it to hang a draft on — this generalises that row to every draft,
-- so ten mocks off one projection are ten rows.
--
-- A draft holds a copy of the numbers it was started against, not a pointer to them: it ranks by
-- what it was started with, the board it came from stays editable while a draft is under way, and
-- deleting that board leaves the draft standing (`source_projection_id` is then nulled by
-- UserProjectionService.delete).

ALTER TABLE user_projections ADD COLUMN source_projection_id UUID;

-- Whether the name is still the server's rather than one the user typed. Only an auto-named draft
-- is renamed by a league sync; a name somebody chose is the more deliberate of the two and stands.
ALTER TABLE user_projections ADD COLUMN auto_named BOOLEAN NOT NULL DEFAULT TRUE;

-- A preset draft is simply a draft that was started from a preset, which `preset` already says.
UPDATE user_projections SET kind = 'DRAFT' WHERE kind = 'PRESET_DRAFT';

-- Every draft still held inside a board becomes a row beside it, carrying that board's rows and
-- corrections (the whole of `data`, draft included) and pointing back at where it came from. A
-- board that follows a share link is included: the picks on it were the follower's own, and a
-- follow is rewritten whenever its author publishes, so they belong outside it. The
-- board's timestamps come with it: when it was last picked in is what the draft list sorts by,
-- and the board's `updated_at` is that moment, since a pick was the last thing written to it.
INSERT INTO user_projections (id, user_id, name, kind, preset, season, data, player_id_space,
                              source_projection_id, auto_named, created_at, updated_at)
SELECT gen_random_uuid(), board.user_id, board.name, 'DRAFT', NULL, board.season, board.data,
       board.player_id_space, board.id, TRUE, board.created_at, board.updated_at
FROM user_projections board
WHERE board.kind <> 'DRAFT'
  AND board.data -> 'draft' IS NOT NULL
  AND jsonb_typeof(board.data -> 'draft') <> 'null';

-- The boards keep their numbers and lose the picks, which now live in the rows above.
UPDATE user_projections
SET data = data - 'draft'
WHERE kind <> 'DRAFT'
  AND data -> 'draft' IS NOT NULL;

-- Drafts are named in a namespace of their own, and two can now collide where nothing could
-- before: a draft just split out takes its board's name, and a preset draft is named after its
-- preset, so a user with a projection called "AI Projection" and a draft against that preset now
-- holds the name twice. The oldest keeps it; the rest are numbered, the same shape V19 used and
-- the same shape the server uses from here on.
--
-- Looping because a suffixed name can land on one that is itself taken, and bounded rather than
-- left to converge: a set of names that will not should fail on the index below and be looked at,
-- not spin forever holding a lock.
DO $$
DECLARE
    renamed  INTEGER;
    attempts INTEGER := 0;
BEGIN
    LOOP
        WITH ranked AS (
            SELECT id,
                   name,
                   row_number() OVER (PARTITION BY user_id, name ORDER BY created_at, id) AS n
            FROM user_projections
            WHERE kind = 'DRAFT'
        )
        UPDATE user_projections p
        SET name = left(r.name, 100 - length(' (' || r.n || ')')) || ' (' || r.n || ')'
        FROM ranked r
        WHERE p.id = r.id
          AND r.n > 1;

        GET DIAGNOSTICS renamed = ROW_COUNT;
        attempts := attempts + 1;
        EXIT WHEN renamed = 0 OR attempts >= 10;
    END LOOP;
END $$;

-- One draft per preset is gone with the model that forced it: drafting against last season's
-- numbers a second time is a reasonable thing to want, and was the whole complaint.
DROP INDEX uk_user_projections_user_preset_draft;

-- The two namespaces, said as two partial indexes. V19's excluded preset drafts from the boards'
-- namespace and V25 narrowed it again to leave out the follows, which their authors name; this
-- excludes every draft from it, and gives the drafts a namespace of their own.
DROP INDEX uk_user_projections_user_name;

CREATE UNIQUE INDEX uk_user_projections_user_name
    ON user_projections (user_id, name)
    WHERE kind <> 'DRAFT' AND origin_share_token IS NULL;

CREATE UNIQUE INDEX uk_user_projections_user_draft_name
    ON user_projections (user_id, name)
    WHERE kind = 'DRAFT';

-- Finding the drafts started from a board, which is what deleting that board has to unhook.
CREATE INDEX idx_user_projections_source_projection_id
    ON user_projections (source_projection_id)
    WHERE source_projection_id IS NOT NULL;
