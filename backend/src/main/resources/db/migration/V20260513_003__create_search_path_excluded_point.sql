CREATE TABLE IF NOT EXISTS search_path_excluded_point (
    id UUID PRIMARY KEY,
    search_path_id UUID NOT NULL,
    point_id VARCHAR(120) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    client_ts TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_search_path_excluded_point_path
        FOREIGN KEY (search_path_id) REFERENCES search_path (id) ON DELETE CASCADE,
    CONSTRAINT chk_search_path_excluded_point_reason CHECK (
        reason IN ('low_accuracy', 'clock_skew', 'invalid_speed', 'distance_jump')
    )
);

CREATE INDEX IF NOT EXISTS idx_search_path_excluded_point_path
    ON search_path_excluded_point (search_path_id, client_ts);
