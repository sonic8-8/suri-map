ALTER TABLE mock112_webhook_event
    DROP CONSTRAINT IF EXISTS chk_mock112_webhook_event_type;

ALTER TABLE mock112_webhook_event
    ADD CONSTRAINT chk_mock112_webhook_event_type
        CHECK (event_type IN ('INCIDENT_READY', 'INCIDENT_ASSIGNMENT_CHANGED', 'INCIDENT_CLOSED'));
