ALTER TABLE search_path_gps_point
    ADD COLUMN location_provider VARCHAR(32),
    ADD COLUMN elapsed_realtime_nanos BIGINT;

ALTER TABLE search_path_excluded_point
    ADD COLUMN lon NUMERIC,
    ADD COLUMN lat NUMERIC,
    ADD COLUMN speed_mps NUMERIC,
    ADD COLUMN horizontal_accuracy_m INTEGER,
    ADD COLUMN location_provider VARCHAR(32),
    ADD COLUMN elapsed_realtime_nanos BIGINT;

ALTER TABLE search_path_excluded_point
    DROP CONSTRAINT IF EXISTS chk_search_path_excluded_point_reason;

ALTER TABLE search_path_excluded_point
    ADD CONSTRAINT chk_search_path_excluded_point_reason CHECK (
        reason IN ('low_accuracy', 'clock_skew', 'invalid_speed', 'distance_jump', 'out_of_order')
    );
