ALTER TABLE user_projections ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'PROJECTION';
ALTER TABLE user_projections ALTER COLUMN kind DROP DEFAULT;

ALTER TABLE user_projections DROP CONSTRAINT user_projections_user_id_name_key;
ALTER TABLE user_projections
    ADD CONSTRAINT uk_user_projections_user_kind_name UNIQUE (user_id, kind, name);
