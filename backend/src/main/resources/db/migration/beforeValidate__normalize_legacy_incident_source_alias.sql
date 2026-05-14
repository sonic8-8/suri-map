DO $$
BEGIN
    IF to_regclass('public.incident') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM incident
        WHERE source_incident_id::text = 'smoke-incident-001'
    )
    AND EXISTS (
        SELECT 1
        FROM incident
        WHERE source_incident_id::text = '00000000-0000-0000-0000-000000000001'
    ) THEN
        RAISE EXCEPTION
            'Cannot normalize legacy incident source alias smoke-incident-001 because canonical source incident UUID already exists';
    END IF;

    UPDATE incident
    SET source_incident_id = '00000000-0000-0000-0000-000000000001'
    WHERE source_incident_id::text = 'smoke-incident-001';
END $$;
