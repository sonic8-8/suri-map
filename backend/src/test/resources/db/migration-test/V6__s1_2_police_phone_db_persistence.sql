ALTER TABLE police_phone
    ADD COLUMN registered BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE police_phone
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE police_phone
    ADD COLUMN heartbeat_sequence BIGINT NOT NULL DEFAULT 0;

ALTER TABLE police_phone
    ADD COLUMN last_heartbeat_event_id UUID;

UPDATE police_phone
SET registered = TRUE
WHERE id IN (
    '00000000-0000-0000-0000-000000000101',
    '50000000-0000-0000-0000-000000000001'
);

INSERT INTO account (
    id, login_id, password_hash, display_name, account_type, organization_type, status
) VALUES (
    '11111111-1111-1111-1111-111111110009',
    'acct-unassigned-phone',
    '{noop}fixture',
    '미배정 폴리폰 계정',
    'TEAM',
    'POLICE_SUBSTATION',
    'ACTIVE'
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
    last_heartbeat_event_id
)
VALUES (
    '00000000-0000-0000-0000-000000000301',
    'dev-unassigned-phone-01',
    '미배정 폴리폰',
    '11111111-1111-1111-1111-111111110009',
    'ACTIVE',
    TRUE,
    NULL,
    NULL,
    1,
    0,
    NULL
);

CREATE TABLE fcm_token (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL,
  police_phone_id UUID NOT NULL,
  app_instance_id VARCHAR(120) NOT NULL,
  token_hash CLOB NOT NULL,
  token_ciphertext CLOB NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at TIMESTAMP WITH TIME ZONE,
  version BIGINT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX ux_fcm_token_police_phone_instance_active
    ON fcm_token (police_phone_id, app_instance_id, status);

CREATE TABLE incident_assignment (
  id UUID PRIMARY KEY,
  incident_id UUID NOT NULL,
  account_id UUID NOT NULL,
  incident_role VARCHAR(32) NOT NULL,
  assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO incident_assignment (
    id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
) VALUES (
    '71000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111110003',
    'MEMBER',
    '2026-05-08T00:00:00Z',
    NULL,
    '2026-05-08T00:00:00Z',
    '2026-05-08T00:00:00Z'
);
