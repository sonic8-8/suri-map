CREATE TABLE IF NOT EXISTS search_area_assignment (
    id UUID PRIMARY KEY,
    search_area_id UUID NOT NULL,
    assigned_account_id UUID NOT NULL,
    assigned_by_account_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    memo TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_area_assignment_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_area_assignment_active
    ON search_area_assignment (search_area_id, assigned_account_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_search_area_assignment_area
    ON search_area_assignment (search_area_id, created_at);

CREATE INDEX IF NOT EXISTS idx_search_area_assignment_assignee
    ON search_area_assignment (assigned_account_id, status);
