CREATE TABLE IF NOT EXISTS mock_webhook_outbox (
    event_id VARCHAR(120) PRIMARY KEY,
    event_type VARCHAR(60) NOT NULL,
    source_incident_id VARCHAR(36) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_mock_webhook_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_mock_webhook_outbox_due
    ON mock_webhook_outbox(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_mock_webhook_outbox_source
    ON mock_webhook_outbox(source_incident_id, created_at);
