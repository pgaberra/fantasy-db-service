-- A preset draft used to be created the moment Start was pressed, before the draft page had
-- asked for the teams and the order, so every setup someone backed out of left a board holding
-- nothing but the seeded rows. The web now creates the board only once that setup is confirmed.
-- These leftovers are no draft to resume, and while one exists the unique index on
-- (user_id, preset) stops that preset being drafted afresh.
--
-- Only rows untouched for an hour: a setup open in a tab still running the old web saves into
-- its board when confirmed, and should not find it gone.
DELETE FROM user_projections
WHERE kind = 'PRESET_DRAFT'
  AND (data -> 'draft' IS NULL OR jsonb_typeof(data -> 'draft') = 'null')
  AND updated_at < now() - INTERVAL '1 hour';
