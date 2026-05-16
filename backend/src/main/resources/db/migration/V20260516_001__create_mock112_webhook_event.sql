CREATE TABLE IF NOT EXISTS mock112_webhook_event (
    event_id VARCHAR(120) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    source_incident_id UUID NOT NULL,
    request_body_hash VARCHAR(128) NOT NULL,
    webhook_status VARCHAR(32) NOT NULL,
    incident_id UUID,
    incident_status VARCHAR(32),
    incident_version BIGINT,
    response_body_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_mock112_webhook_event_type CHECK (
        event_type IN ('INCIDENT_READY', 'INCIDENT_ASSIGNMENT_CHANGED')
    ),
    CONSTRAINT chk_mock112_webhook_status CHECK (
        webhook_status IN ('RESERVED', 'COMPLETED')
    )
);

CREATE INDEX IF NOT EXISTS idx_mock112_webhook_event_source
    ON mock112_webhook_event (source_incident_id, created_at);
