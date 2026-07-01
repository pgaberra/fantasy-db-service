-- Delete pre-#141 legacy draft blobs whose picks still carry the removed `by`
-- field. #43 tolerated that unknown field on read; we have reverted that
-- tolerance for a strict model, so the offending draft is deleted instead of
-- accommodated. These legacy drafts are already discarded by the web
-- (sanitizeDraft), so dropping the whole `draft` key only pre-empts that;
-- the projection's settings and players are left untouched.
UPDATE user_projections
SET data = data - 'draft'
WHERE jsonb_path_exists(data, '$.draft.picks[*].by');
