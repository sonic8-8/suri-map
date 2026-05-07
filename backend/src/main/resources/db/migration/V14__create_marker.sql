CREATE TABLE IF NOT EXISTS marker (
    id UUID PRIMARY KEY,
    operational_period_id UUID NOT NULL,
    duty_shift_id UUID,
    marker_type VARCHAR(40) NOT NULL,
    support_request_type VARCHAR(40),
    location GEOMETRY(POINT, 4326) NOT NULL,
    memo TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL,
    police_phone_id UUID,
    marker_source VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_marker_type CHECK (
        marker_type IN ('CLUE', 'PERSON_FOUND', 'FIELD_CONDITION', 'SUPPORT_REQUEST', 'NOTE')
    ),
    CONSTRAINT chk_marker_support_request_type CHECK (
        support_request_type IS NULL OR support_request_type IN ('DRONE', 'POLICE_DOG', 'OTHER')
    ),
    CONSTRAINT chk_marker_source CHECK (marker_source IN ('APP', 'WEB', 'MOCK_SEED', 'SYSTEM')),
    CONSTRAINT chk_marker_status CHECK (status IN ('ACTIVE', 'UPDATED', 'DELETED')),
    CONSTRAINT chk_marker_version_positive CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_marker_op_status
    ON marker (operational_period_id, status);

CREATE INDEX IF NOT EXISTS idx_marker_type
    ON marker (marker_type);

CREATE INDEX IF NOT EXISTS idx_marker_police_phone
    ON marker (police_phone_id);

CREATE INDEX IF NOT EXISTS idx_marker_location
    ON marker
    USING GIST (location);
