-- What an import copies. The snapshot in `data` holds the ranked rows the public page shows —
-- a teaser, capped well below a full roster — which is too few players to draft against. The
-- board is every row the projection had when it was shared, kept out of `data` so the public
-- endpoint never serialises half a megabyte to a visitor who came to read a top list.
ALTER TABLE projection_shares ADD COLUMN board JSONB;

-- Backfill from the projection each share points at. Pre-launch this is close enough to what
-- the board was at share time, and the alternative is dropping links that already work.
UPDATE projection_shares s
SET board = jsonb_build_object('players', p.data -> 'players')
FROM user_projections p
WHERE p.id = s.projection_id
  AND p.data -> 'players' IS NOT NULL;

DELETE FROM projection_shares WHERE board IS NULL;

ALTER TABLE projection_shares ALTER COLUMN board SET NOT NULL;

-- Where an imported projection came from, stamped at import time rather than read live: the
-- copy is frozen, so the credit on it is too, and it has to survive the share going away.
ALTER TABLE user_projections ADD COLUMN origin_share_token VARCHAR(64);
ALTER TABLE user_projections ADD COLUMN origin_author_username VARCHAR(20);
