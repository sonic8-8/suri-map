TRUNCATE TABLE idempotency_record;
TRUNCATE TABLE event_dispatch_job,
    search_area_boundary_alert,
    search_path_lifecycle_event,
    search_path_excluded_point,
    search_path_segment,
    search_path;

DELETE FROM duty_shift
WHERE id IN (
    '71000000-0000-0000-0000-000000002622'::uuid,
    '60000000-0000-0000-0000-000000002621'::uuid
);
DELETE FROM operational_period
WHERE id = '65000000-0000-0000-0000-000000002621'::uuid;
DELETE FROM incident_assignment
WHERE id IN (
    '61000000-0000-0000-0000-000000002622'::uuid,
    '61000000-0000-0000-0000-000000002621'::uuid
);
DELETE FROM police_phone
WHERE id IN (
    '50000000-0000-0000-0000-000000002622'::uuid,
    '50000000-0000-0000-0000-000000000001'::uuid
)
   OR phone_code = 'dev-precinct-car-01';
DELETE FROM account
WHERE id IN (
    '62000000-0000-0000-0000-000000002621'::uuid,
    '63000000-0000-0000-0000-000000002621'::uuid
);
DELETE FROM incident
WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid;

INSERT INTO incident (
    id,
    source_incident_id,
    title,
    status,
    opened_at,
    version,
    created_at,
    updated_at
)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid,
    '64000000-0000-0000-0000-000000002621'::uuid,
    'S3-1 persistence incident',
    'OPEN',
    '2026-04-28T00:00:00Z'::timestamptz,
    1,
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz
);

INSERT INTO account (
    id,
    login_id,
    password_hash,
    display_name,
    account_type,
    organization_type,
    status,
    created_at,
    updated_at
)
VALUES
    (
        '62000000-0000-0000-0000-000000002621'::uuid,
        'acct-path-persistence',
        '{noop}fixture',
        'Path persistence account',
        'PATROL_CAR',
        'POLICE_SUBSTATION',
        'ACTIVE',
        '2026-04-28T00:00:00Z'::timestamptz,
        '2026-04-28T00:00:00Z'::timestamptz
    ),
    (
        '63000000-0000-0000-0000-000000002621'::uuid,
        'acct-path-corrector',
        '{noop}fixture',
        'Path corrector account',
        'COMMAND',
        'POLICE_SUBSTATION',
        'ACTIVE',
        '2026-04-28T00:00:00Z'::timestamptz,
        '2026-04-28T00:00:00Z'::timestamptz
    );

INSERT INTO police_phone (
    id,
    phone_code,
    display_name,
    account_id,
    status,
    registered,
    last_heartbeat_at,
    last_sync_at,
    version,
    created_at,
    updated_at
)
VALUES (
    '50000000-0000-0000-0000-000000000001'::uuid,
    'dev-precinct-car-01',
    'Path persistence phone',
    '62000000-0000-0000-0000-000000002621'::uuid,
    'ACTIVE',
    TRUE,
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz,
    1,
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz
);

INSERT INTO incident_assignment (
    id,
    incident_id,
    account_id,
    incident_role,
    assigned_at,
    created_at,
    updated_at
)
VALUES (
    '61000000-0000-0000-0000-000000002621'::uuid,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid,
    '62000000-0000-0000-0000-000000002621'::uuid,
    'MEMBER',
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz
);

INSERT INTO operational_period (
    id,
    incident_id,
    sequence_number,
    status,
    reason,
    started_by_account_id,
    started_at,
    version,
    created_at,
    updated_at
)
VALUES (
    '65000000-0000-0000-0000-000000002621'::uuid,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid,
    1,
    'ACTIVE',
    'INITIAL_IMPORT',
    '62000000-0000-0000-0000-000000002621'::uuid,
    '2026-04-28T00:00:00Z'::timestamptz,
    1,
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz
);

INSERT INTO duty_shift (
    id,
    operational_period_id,
    incident_assignment_id,
    police_phone_id,
    status,
    started_by_account_id,
    started_at,
    version,
    created_at,
    updated_at
)
VALUES (
    '60000000-0000-0000-0000-000000002621'::uuid,
    '65000000-0000-0000-0000-000000002621'::uuid,
    '61000000-0000-0000-0000-000000002621'::uuid,
    '50000000-0000-0000-0000-000000000001'::uuid,
    'ACTIVE',
    '62000000-0000-0000-0000-000000002621'::uuid,
    '2026-04-28T00:00:00Z'::timestamptz,
    1,
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz
);
