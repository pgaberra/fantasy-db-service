-- Which platform's player ids a stored row is keyed by.
--
-- Everything saved so far is keyed by Yahoo's ids, because Yahoo was the only player source.
-- Yahoo has stopped serving the player collection and ESPN now provides the pool, which numbers
-- the same people differently — so the stored ids have to be remapped once, and a row has to be
-- able to say which side of that it is on.
--
-- Without the marker the remap could not be run twice safely: the two id spaces overlap in
-- range (a low ESPN id is a plausible Yahoo id), so a second pass over an already-remapped row
-- could translate an id that was never Yahoo's.

ALTER TABLE user_projections
    ADD COLUMN player_id_space VARCHAR(8) NOT NULL DEFAULT 'yahoo';

ALTER TABLE projection_shares
    ADD COLUMN player_id_space VARCHAR(8) NOT NULL DEFAULT 'yahoo';
