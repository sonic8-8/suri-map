ALTER TABLE police_phone
    ADD COLUMN IF NOT EXISTS heartbeat_sequence BIGINT NOT NULL DEFAULT 0;

ALTER TABLE police_phone
    ADD COLUMN IF NOT EXISTS last_heartbeat_event_id UUID;

UPDATE police_phone
SET registered = TRUE
WHERE phone_code IN (
    'dev-precinct-car-01',
    'dev-precinct-phone-01',
    'dev-alpha-phone-01',
    'dev-support-car-01',
    'dev-support-phone-01'
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
    heartbeat_sequence,
    last_heartbeat_event_id,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000301',
    'dev-unassigned-phone-01',
    'Unassigned fixture phone',
    '11111111-1111-1111-1111-111111110009',
    'ACTIVE',
    TRUE,
    NULL,
    NULL,
    1,
    0,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    phone_code = EXCLUDED.phone_code,
    display_name = EXCLUDED.display_name,
    account_id = EXCLUDED.account_id,
    status = EXCLUDED.status,
    registered = EXCLUDED.registered,
    updated_at = CURRENT_TIMESTAMP;
