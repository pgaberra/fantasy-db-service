-- Which preset a draft was started from. A user may hold one draft per preset, so this is also
-- what keeps two of them apart; before it there was only one preset and the kind was enough.
ALTER TABLE user_projections ADD COLUMN preset VARCHAR(20);

-- The drafts already stored predate the column. Every preset draft written before the AI
-- preset shipped came from last season's stats, and the two are still told apart by the name
-- the server gave them — the last time that name is load-bearing.
UPDATE user_projections SET preset = 'MODEL'
WHERE kind = 'PRESET_DRAFT' AND name = 'AI Projection';

UPDATE user_projections SET preset = 'LAST_SEASON'
WHERE kind = 'PRESET_DRAFT' AND preset IS NULL;
