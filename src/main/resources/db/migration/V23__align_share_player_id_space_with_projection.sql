-- A share was never stamped with the id space its rows were keyed by, so every share took the
-- column default 'yahoo', including those published from a projection already on ESPN's ids.
-- Its rows come from the same pool as its projection's, and a remap moves the two together, so the
-- projection's stamp is the share's.
UPDATE projection_shares s
SET player_id_space = p.player_id_space
FROM user_projections p
WHERE s.projection_id = p.id
  AND s.player_id_space <> p.player_id_space;
