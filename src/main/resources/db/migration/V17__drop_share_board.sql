-- The board behind a share is no longer stored twice. V16 kept it out of `data` because the
-- snapshot there was a capped teaser — too few players to draft against — so an import needed its
-- own copy. `data` now holds the whole ranking, and a shared row carries everything a stored
-- player row does (id, type, stats) on top of the identity and rank the public page renders, so
-- an import derives its rows from `data` and this column is duplication.
--
-- A share published before `data` held the whole board keeps only the rows it published, which is
-- the snapshot its link has always shown. Nothing that was ever public is lost.
ALTER TABLE projection_shares DROP COLUMN board;
