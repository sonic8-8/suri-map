ALTER TABLE mock_incident
    ADD COLUMN IF NOT EXISTS case_number VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mock_incident_case_number
    ON mock_incident(case_number);
