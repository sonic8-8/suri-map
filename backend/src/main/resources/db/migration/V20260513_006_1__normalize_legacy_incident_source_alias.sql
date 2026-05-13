DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM incident
        WHERE source_incident_id = 'smoke-incident-001'
    )
    AND EXISTS (
        SELECT 1
        FROM incident
        WHERE source_incident_id = '00000000-0000-0000-0000-000000000001'
    ) THEN
        RAISE EXCEPTION
            'Cannot normalize legacy incident source alias smoke-incident-001 because canonical source incident UUID already exists';
    END IF;
END $$;

UPDATE incident
SET source_incident_id = '00000000-0000-0000-0000-000000000001'
WHERE source_incident_id = 'smoke-incident-001';
