ALTER TABLE mock_incident
    DROP CONSTRAINT IF EXISTS chk_mock_incident_status;

ALTER TABLE mock_incident
    ADD CONSTRAINT chk_mock_incident_status
        CHECK (status IN ('READY', 'IMPORTED', 'CLOSED'));
