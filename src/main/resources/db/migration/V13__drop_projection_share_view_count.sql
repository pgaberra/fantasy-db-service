-- The counter counted crawlers, not people: a link posted in a chat is fetched twice for its
-- preview before anyone clicks it, so the number shown to the owner meant nothing. Dropped
-- rather than corrected until we decide what a "view" should be.
ALTER TABLE projection_shares DROP COLUMN view_count;
