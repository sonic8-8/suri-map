CREATE TABLE IF NOT EXISTS search_area_history (
    id UUID PRIMARY KEY,
    area_id UUID NOT NULL,
    incident_id UUID NOT NULL,
    op_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_state VARCHAR(32),
    next_state VARCHAR(32) NOT NULL,
    previous_geometry GEOMETRY(POLYGON, 4326),
    next_geometry GEOMETRY(POLYGON, 4326) NOT NULL,
    memo TEXT,
    changed_by_account_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_search_area_history_event_type CHECK (
        event_type IN (
            'CREATED',
            'GEOMETRY_UPDATED',
            'SPLIT_FROM_PARENT',
            'STATE_CHANGED'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_search_area_history_area
    ON search_area_history (area_id, changed_at);

CREATE INDEX IF NOT EXISTS idx_search_area_history_op
    ON search_area_history (op_id, changed_at);
