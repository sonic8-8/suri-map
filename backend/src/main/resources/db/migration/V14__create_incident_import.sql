CREATE TABLE IF NOT EXISTS incident (
    id UUID PRIMARY KEY,
    source_incident_id VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    closed_by_account_id UUID,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_incident_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_incident_source_incident_id
    ON incident (source_incident_id);

CREATE INDEX IF NOT EXISTS idx_incident_status
    ON incident (status);

CREATE TABLE IF NOT EXISTS missing_person (
    incident_id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    photo_object_key TEXT,
    appearance_text TEXT,
    last_seen_location_text VARCHAR(255),
    last_seen_at TIMESTAMPTZ,
    imported_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS incident_assignment (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    account_id VARCHAR(80) NOT NULL,
    incident_role VARCHAR(32) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_incident_assignment_role CHECK (
        incident_role IN ('MEMBER', 'FIELD_COMMANDER', 'INCIDENT_COMMANDER')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_incident_assignment_active_account
    ON incident_assignment (incident_id, account_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_incident_assignment_incident_role
    ON incident_assignment (incident_id, incident_role);

CREATE INDEX IF NOT EXISTS idx_incident_assignment_account
    ON incident_assignment (account_id);

CREATE TABLE IF NOT EXISTS idempotency_record (
    id UUID PRIMARY KEY,
    client_operation_id VARCHAR(120),
    incident_id UUID,
    police_phone_id UUID,
    idempotency_key VARCHAR(160) NOT NULL,
    request_body_hash CHAR(64) NOT NULL,
    request_path VARCHAR(200) NOT NULL,
    request_method VARCHAR(16) NOT NULL,
    idempotency_status VARCHAR(32) NOT NULL,
    response_status_code INTEGER,
    response_body_json TEXT,
    response_body_format_version INTEGER,
    result_entity_type VARCHAR(80),
    result_entity_id UUID,
    result_entity_status VARCHAR(32),
    result_entity_version BIGINT,
    client_requested_at TIMESTAMPTZ,
    clock_offset_ms BIGINT,
    replay_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_idempotency_status CHECK (
        idempotency_status IN ('RESERVED', 'COMPLETED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_idempotency_record_key_path_method
    ON idempotency_record (idempotency_key, request_path, request_method);
