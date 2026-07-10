DELETE FROM duty_shift
WHERE id = '71000000-0000-0000-0000-000000002622'::uuid;
DELETE FROM incident_assignment
WHERE id = '61000000-0000-0000-0000-000000002622'::uuid;
DELETE FROM police_phone
WHERE id = '50000000-0000-0000-0000-000000002622'::uuid;

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
    '50000000-0000-0000-0000-000000002622'::uuid,
    'dev-path-other-phone',
    'Other path phone',
    '63000000-0000-0000-0000-000000002621'::uuid,
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
    '61000000-0000-0000-0000-000000002622'::uuid,
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid,
    '63000000-0000-0000-0000-000000002621'::uuid,
    'MEMBER',
    '2026-04-28T00:00:00Z'::timestamptz,
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
    '71000000-0000-0000-0000-000000002622'::uuid,
    '65000000-0000-0000-0000-000000002621'::uuid,
    '61000000-0000-0000-0000-000000002622'::uuid,
    '50000000-0000-0000-0000-000000002622'::uuid,
    'ACTIVE',
    '63000000-0000-0000-0000-000000002621'::uuid,
    '2026-04-28T00:00:00Z'::timestamptz,
    1,
    '2026-04-28T00:00:00Z'::timestamptz,
    '2026-04-28T00:00:00Z'::timestamptz
);
