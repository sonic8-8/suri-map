ALTER TABLE incident
    ALTER COLUMN source_incident_id TYPE UUID
    USING source_incident_id::UUID;
