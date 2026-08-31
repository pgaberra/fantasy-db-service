-- One name per user, across everything the user can see and name.
--
-- The old constraint was (user_id, kind, name), so a projection someone made and a board they
-- imported could hold the same name and sit next to each other in the same list, told apart only
-- by the smaller line under them. Names are what the lists are read by, so the namespace is
-- widened to (user_id, name) — but only over the kinds a user names. A PRESET_DRAFT is named by
-- the server after its preset, is never listed as the user's own work, and must not be able to
-- collide with a projection somebody happened to call "AI Projection".

-- Existing rows may already break the new rule (two did on staging, both imported copies of the
-- user's own boards), and a unique index cannot be created over them. The oldest keeps the name
-- it has; every later one takes a numbered suffix, truncated so it still fits the column.
--
-- Looping because a suffixed name can land on one that is itself taken. Bounded rather than
-- `LOOP ... EXIT WHEN` alone: if some pathological set of names will not converge, the migration
-- should fail on the index below and be looked at, not spin forever holding a lock.
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
            WHERE kind <> 'PRESET_DRAFT'
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

ALTER TABLE user_projections DROP CONSTRAINT uk_user_projections_user_kind_name;

CREATE UNIQUE INDEX uk_user_projections_user_name
    ON user_projections (user_id, name)
    WHERE kind <> 'PRESET_DRAFT';

-- What the dropped constraint was also doing for preset drafts, said directly. A user may hold
-- one draft per preset; that was enforced in code alone (UserProjectionService.create) with the
-- old constraint standing behind it by way of the server-chosen name. This is the same rule as
-- the code's, so two requests racing each other cannot both get through.
CREATE UNIQUE INDEX uk_user_projections_user_preset_draft
    ON user_projections (user_id, preset)
    WHERE kind = 'PRESET_DRAFT';
