CREATE TABLE IF NOT EXISTS marker_notification (
    id UUID PRIMARY KEY,
    marker_id UUID NOT NULL,
    notification_type VARCHAR(80) NOT NULL,
    recipient_rule VARCHAR(60) NOT NULL,
    recipient_account_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    recipient_police_phone_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    notification_payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_marker_notification_type CHECK (
        notification_type IN ('SUPPORT_REQUEST_CREATED', 'PERSON_FOUND')
    ),
    CONSTRAINT chk_marker_notification_recipient_rule CHECK (
        recipient_rule IN ('COMMANDERS_AND_FIELD_COMMANDERS', 'ALL_INCIDENT_ASSIGNED')
    ),
    CONSTRAINT chk_marker_notification_status CHECK (
        status IN ('SNAPSHOT_CREATED', 'SNAPSHOT_CANCELLED')
    ),
    CONSTRAINT chk_marker_notification_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_marker_notification_marker
    ON marker_notification (marker_id);

CREATE INDEX IF NOT EXISTS idx_marker_notification_type
    ON marker_notification (notification_type);
