DROP INDEX IF EXISTS idx_search_path_lifecycle_event_actor_phone;

ALTER TABLE search_path_lifecycle_event
    DROP COLUMN IF EXISTS actor_police_phone_id;
