CREATE TABLE IF NOT EXISTS search_area_history (
    id UUID PRIMARY KEY,
    search_area_id UUID NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    previous_status VARCHAR(24),
    next_status VARCHAR(24),
    previous_geometry GEOMETRY(POLYGON, 4326),
    next_geometry GEOMETRY(POLYGON, 4326),
    change_memo TEXT,
    changed_by_account_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_area_history_change_type CHECK (
        change_type IN ('CREATED', 'GEOMETRY_UPDATED', 'STATUS_CHANGED', 'SPLIT')
    )
);

CREATE INDEX IF NOT EXISTS idx_search_area_history_area
    ON search_area_history (search_area_id, changed_at);
