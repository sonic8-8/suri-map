-- Suri-Map frontend mock data set #2.
-- Existing seed account IDs are referenced. Additional test2-only accounts and PolicePhones are inserted below.
-- Includes one newly imported incident with an active OP but no search_area rows.

SET client_encoding = 'UTF8';
CREATE EXTENSION IF NOT EXISTS postgis;

BEGIN;

DELETE FROM fcm_token
WHERE police_phone_id IN (
  '00000000-0000-0000-0000-000000000401',
  '00000000-0000-0000-0000-000000000402',
  '00000000-0000-0000-0000-000000000403',
  '00000000-0000-0000-0000-000000000404',
  '00000000-0000-0000-0000-000000000405',
  '00000000-0000-0000-0000-000000000406'
);

DELETE FROM refresh_token
WHERE police_phone_id IN (
  '00000000-0000-0000-0000-000000000401',
  '00000000-0000-0000-0000-000000000402',
  '00000000-0000-0000-0000-000000000403',
  '00000000-0000-0000-0000-000000000404',
  '00000000-0000-0000-0000-000000000405',
  '00000000-0000-0000-0000-000000000406'
);

DELETE FROM offline_package_installation
WHERE offline_package_manifest_id IN (
  SELECT id
  FROM offline_package_manifest
  WHERE incident_id IN (
    '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
    'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2',
    '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
    'bd57c6d4-a791-4617-b15b-40dd0a5137aa',
    '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb'
  )
);

DELETE FROM offline_package_manifest
WHERE incident_id IN (
  '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
  'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2',
  '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
  'bd57c6d4-a791-4617-b15b-40dd0a5137aa',
  '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb'
);

DELETE FROM search_history_summary
WHERE operational_period_id IN (
  '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
  '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
  'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
  '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
  'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
  'a3a1c010-4437-4878-87ce-f0bd7841fe75',
  'f550b765-6216-46d1-8e58-ed0fb33ff97d'
);

DELETE FROM handover_memo
WHERE operational_period_id IN (
  '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
  '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
  'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
  '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
  'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
  'a3a1c010-4437-4878-87ce-f0bd7841fe75',
  'f550b765-6216-46d1-8e58-ed0fb33ff97d'
);

DELETE FROM marker_notification
WHERE marker_id IN (
  SELECT id
  FROM marker
  WHERE operational_period_id IN (
    '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'f550b765-6216-46d1-8e58-ed0fb33ff97d'
  )
);

DELETE FROM photo
WHERE marker_id IN (
  SELECT id
  FROM marker
  WHERE operational_period_id IN (
    '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'f550b765-6216-46d1-8e58-ed0fb33ff97d'
  )
);

DELETE FROM marker
WHERE operational_period_id IN (
  '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
  '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
  'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
  '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
  'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
  'a3a1c010-4437-4878-87ce-f0bd7841fe75',
  'f550b765-6216-46d1-8e58-ed0fb33ff97d'
);

DELETE FROM search_path_excluded_point
WHERE search_path_id IN (
  SELECT sp.id
  FROM search_path sp
  JOIN duty_shift ds ON ds.id = sp.duty_shift_id
  WHERE ds.operational_period_id IN (
    '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'f550b765-6216-46d1-8e58-ed0fb33ff97d'
  )
);

DELETE FROM search_path_segment
WHERE search_path_id IN (
  SELECT sp.id
  FROM search_path sp
  JOIN duty_shift ds ON ds.id = sp.duty_shift_id
  WHERE ds.operational_period_id IN (
    '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'f550b765-6216-46d1-8e58-ed0fb33ff97d'
  )
);

DELETE FROM search_path
WHERE duty_shift_id IN (
  SELECT id
  FROM duty_shift
  WHERE operational_period_id IN (
    '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'f550b765-6216-46d1-8e58-ed0fb33ff97d'
  )
);

DELETE FROM duty_shift
WHERE operational_period_id IN (
  '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
  '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
  'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
  '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
  'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
  'a3a1c010-4437-4878-87ce-f0bd7841fe75',
  'f550b765-6216-46d1-8e58-ed0fb33ff97d'
);

DELETE FROM search_area_assignment
WHERE search_area_id IN (
  SELECT id
  FROM search_area
  WHERE operational_period_id IN (
    '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'f550b765-6216-46d1-8e58-ed0fb33ff97d'
  )
);

DELETE FROM search_area_history
WHERE search_area_id IN (
  SELECT id
  FROM search_area
  WHERE operational_period_id IN (
    '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'f550b765-6216-46d1-8e58-ed0fb33ff97d'
  )
);

DELETE FROM search_area
WHERE operational_period_id IN (
  '4bfe3a19-1270-4924-b581-ef7c4fc7d101',
  '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
  'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
  '78a8df07-3248-4fa3-b2df-7d9b8cb36daa',
  'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
  'a3a1c010-4437-4878-87ce-f0bd7841fe75',
  'f550b765-6216-46d1-8e58-ed0fb33ff97d'
);

DELETE FROM operational_period
WHERE incident_id IN (
  '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
  'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2',
  '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
  'bd57c6d4-a791-4617-b15b-40dd0a5137aa',
  '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb'
);

DELETE FROM incident_assignment
WHERE incident_id IN (
  '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
  'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2',
  '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
  'bd57c6d4-a791-4617-b15b-40dd0a5137aa',
  '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb'
);

DELETE FROM missing_person
WHERE incident_id IN (
  '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
  'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2',
  '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
  'bd57c6d4-a791-4617-b15b-40dd0a5137aa',
  '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb'
);

DELETE FROM incident
WHERE id IN (
  '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
  'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2',
  '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
  'bd57c6d4-a791-4617-b15b-40dd0a5137aa',
  '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb'
);

INSERT INTO account (
  id,
  login_id,
  display_name,
  account_type,
  organization_type,
  password_hash,
  status,
  created_at,
  updated_at
)
VALUES
  ('11111111-1111-1111-1111-111111110401', 'acct-test2-river-team', 'Test2 river field team', 'TEAM', 'MISSING_TEAM', '{noop}fixture', 'ACTIVE', '2026-05-01T08:00:00+09:00', '2026-05-01T08:00:00+09:00'),
  ('11111111-1111-1111-1111-111111110402', 'acct-test2-river-car', 'Test2 river patrol car', 'PATROL_CAR', 'POLICE_SUBSTATION', '{noop}fixture', 'ACTIVE', '2026-05-01T08:00:00+09:00', '2026-05-01T08:00:00+09:00'),
  ('11111111-1111-1111-1111-111111110403', 'acct-test2-support-team', 'Test2 support field team', 'TEAM', 'SUPPORT_UNIT', '{noop}fixture', 'ACTIVE', '2026-05-01T08:00:00+09:00', '2026-05-01T08:00:00+09:00'),
  ('11111111-1111-1111-1111-111111110404', 'acct-test2-night-team', 'Test2 night field team', 'TEAM', 'MISSING_TEAM', '{noop}fixture', 'ACTIVE', '2026-05-01T18:30:00+09:00', '2026-05-01T18:30:00+09:00'),
  ('11111111-1111-1111-1111-111111110405', 'acct-test2-disabled-phone', 'Test2 disabled phone account', 'TEAM', 'POLICE_SUBSTATION', '{noop}fixture', 'ACTIVE', '2026-05-01T08:00:00+09:00', '2026-05-01T08:00:00+09:00'),
  ('11111111-1111-1111-1111-111111110406', 'acct-test2-unregistered-phone', 'Test2 unregistered phone account', 'TEAM', 'SUPPORT_UNIT', '{noop}fixture', 'ACTIVE', '2026-05-01T08:00:00+09:00', '2026-05-01T08:00:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  login_id = EXCLUDED.login_id,
  display_name = EXCLUDED.display_name,
  account_type = EXCLUDED.account_type,
  organization_type = EXCLUDED.organization_type,
  password_hash = EXCLUDED.password_hash,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at;

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
VALUES
  ('00000000-0000-0000-0000-000000000401', 'test2-river-team-phone-01', 'Test2 river team phone', '11111111-1111-1111-1111-111111110401', 'ACTIVE', TRUE, '2026-05-01T10:08:00+09:00', '2026-05-01T09:44:00+09:00', 7, 42, '61000000-0000-0000-0000-000000000401', '2026-05-01T08:00:00+09:00', '2026-05-01T10:08:00+09:00'),
  ('00000000-0000-0000-0000-000000000402', 'test2-river-car-phone-01', 'Test2 river patrol phone', '11111111-1111-1111-1111-111111110402', 'ACTIVE', TRUE, '2026-05-01T10:03:00+09:00', '2026-05-01T09:32:00+09:00', 5, 27, '61000000-0000-0000-0000-000000000402', '2026-05-01T08:00:00+09:00', '2026-05-01T10:03:00+09:00'),
  ('00000000-0000-0000-0000-000000000403', 'test2-support-team-phone-01', 'Test2 support team phone', '11111111-1111-1111-1111-111111110403', 'ACTIVE', TRUE, '2026-05-01T12:05:00+09:00', '2026-05-01T11:58:00+09:00', 4, 18, '61000000-0000-0000-0000-000000000403', '2026-05-01T08:00:00+09:00', '2026-05-01T12:05:00+09:00'),
  ('00000000-0000-0000-0000-000000000404', 'test2-night-team-phone-01', 'Test2 night team phone', '11111111-1111-1111-1111-111111110404', 'ACTIVE', TRUE, '2026-05-01T21:12:00+09:00', '2026-05-01T20:55:00+09:00', 6, 31, '61000000-0000-0000-0000-000000000404', '2026-05-01T18:30:00+09:00', '2026-05-01T21:12:00+09:00'),
  ('00000000-0000-0000-0000-000000000405', 'test2-disabled-phone-01', 'Test2 disabled phone', '11111111-1111-1111-1111-111111110405', 'DISABLED', TRUE, '2026-05-01T08:35:00+09:00', NULL, 2, 3, NULL, '2026-05-01T08:00:00+09:00', '2026-05-01T08:35:00+09:00'),
  ('00000000-0000-0000-0000-000000000406', 'test2-unregistered-phone-01', 'Test2 unregistered phone', '11111111-1111-1111-1111-111111110406', 'ACTIVE', FALSE, NULL, NULL, 1, 0, NULL, '2026-05-01T08:00:00+09:00', '2026-05-01T08:00:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  phone_code = EXCLUDED.phone_code,
  display_name = EXCLUDED.display_name,
  account_id = EXCLUDED.account_id,
  status = EXCLUDED.status,
  registered = EXCLUDED.registered,
  last_heartbeat_at = EXCLUDED.last_heartbeat_at,
  last_sync_at = EXCLUDED.last_sync_at,
  version = EXCLUDED.version,
  heartbeat_sequence = EXCLUDED.heartbeat_sequence,
  last_heartbeat_event_id = EXCLUDED.last_heartbeat_event_id,
  updated_at = EXCLUDED.updated_at;

INSERT INTO refresh_token (
  id,
  account_id,
  police_phone_id,
  channel,
  token_hash,
  status,
  expires_at,
  revoked_at,
  created_at
)
VALUES
  ('62000000-0000-0000-0000-000000000401', '11111111-1111-1111-1111-111111110401', '00000000-0000-0000-0000-000000000401', 'APP', 'test2-refresh-river-team-active', 'ACTIVE', '2026-05-08T10:08:00+09:00', NULL, '2026-05-01T08:10:00+09:00'),
  ('62000000-0000-0000-0000-000000000402', '11111111-1111-1111-1111-111111110402', '00000000-0000-0000-0000-000000000402', 'APP', 'test2-refresh-river-car-revoked', 'REVOKED', '2026-05-08T10:03:00+09:00', '2026-05-01T09:50:00+09:00', '2026-05-01T08:12:00+09:00'),
  ('62000000-0000-0000-0000-000000000403', '11111111-1111-1111-1111-111111110403', '00000000-0000-0000-0000-000000000403', 'APP', 'test2-refresh-support-team-active', 'ACTIVE', '2026-05-08T12:05:00+09:00', NULL, '2026-05-01T11:35:00+09:00'),
  ('62000000-0000-0000-0000-000000000404', '11111111-1111-1111-1111-111111110404', '00000000-0000-0000-0000-000000000404', 'APP', 'test2-refresh-night-team-expired', 'EXPIRED', '2026-05-01T21:00:00+09:00', NULL, '2026-05-01T19:00:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  account_id = EXCLUDED.account_id,
  police_phone_id = EXCLUDED.police_phone_id,
  channel = EXCLUDED.channel,
  token_hash = EXCLUDED.token_hash,
  status = EXCLUDED.status,
  expires_at = EXCLUDED.expires_at,
  revoked_at = EXCLUDED.revoked_at;

INSERT INTO fcm_token (
  id,
  account_id,
  police_phone_id,
  app_instance_id,
  token_hash,
  token_ciphertext,
  status,
  created_at,
  last_registered_at,
  revoked_at,
  version
)
VALUES
  ('63000000-0000-0000-0000-000000000401', '11111111-1111-1111-1111-111111110401', '00000000-0000-0000-0000-000000000401', 'test2-river-team-app', 'test2-fcm-river-team-hash', 'ciphertext:test2-river-team', 'ACTIVE', '2026-05-01T08:10:00+09:00', '2026-05-01T10:07:00+09:00', NULL, 4),
  ('63000000-0000-0000-0000-000000000402', '11111111-1111-1111-1111-111111110402', '00000000-0000-0000-0000-000000000402', 'test2-river-car-app', 'test2-fcm-river-car-hash', 'ciphertext:test2-river-car', 'REVOKED', '2026-05-01T08:12:00+09:00', '2026-05-01T09:10:00+09:00', '2026-05-01T09:50:00+09:00', 2),
  ('63000000-0000-0000-0000-000000000403', '11111111-1111-1111-1111-111111110403', '00000000-0000-0000-0000-000000000403', 'test2-support-team-app', 'test2-fcm-support-team-hash', 'ciphertext:test2-support-team', 'ACTIVE', '2026-05-01T11:35:00+09:00', '2026-05-01T12:04:00+09:00', NULL, 3),
  ('63000000-0000-0000-0000-000000000404', '11111111-1111-1111-1111-111111110404', '00000000-0000-0000-0000-000000000404', 'test2-night-team-app', 'test2-fcm-night-team-hash', 'ciphertext:test2-night-team', 'ACTIVE', '2026-05-01T19:00:00+09:00', '2026-05-01T21:11:00+09:00', NULL, 5)
ON CONFLICT (id) DO UPDATE SET
  account_id = EXCLUDED.account_id,
  police_phone_id = EXCLUDED.police_phone_id,
  app_instance_id = EXCLUDED.app_instance_id,
  token_hash = EXCLUDED.token_hash,
  token_ciphertext = EXCLUDED.token_ciphertext,
  status = EXCLUDED.status,
  last_registered_at = EXCLUDED.last_registered_at,
  revoked_at = EXCLUDED.revoked_at,
  version = EXCLUDED.version;

INSERT INTO incident (
  id,
  source_incident_id,
  title,
  status,
  opened_at,
  closed_at,
  closed_by_account_id,
  version,
  created_at,
  updated_at
)
VALUES
  ('8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '00000000-0000-0000-0000-000000000101', '한강공원 산책로 실종자 수색', 'OPEN', '2026-05-01T08:10:00+09:00', NULL, NULL, 3, '2026-05-01T08:10:00+09:00', '2026-05-01T09:40:00+09:00'),
  ('e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', '00000000-0000-0000-0000-000000000102', '도심 외곽 골목 실종자 수색', 'OPEN', '2026-05-01T11:20:00+09:00', NULL, NULL, 1, '2026-05-01T11:20:00+09:00', '2026-05-01T11:20:00+09:00'),
  ('6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', '00000000-0000-0000-0000-000000000103', '하천변 야간 실종자 수색', 'OPEN', '2026-05-01T19:35:00+09:00', NULL, NULL, 4, '2026-05-01T19:35:00+09:00', '2026-05-01T21:15:00+09:00'),
  ('bd57c6d4-a791-4617-b15b-40dd0a5137aa', '00000000-0000-0000-0000-000000000104', '터미널 주변 단기 수색 종료 사건', 'CLOSED', '2026-04-30T14:00:00+09:00', '2026-04-30T16:30:00+09:00', '11111111-1111-1111-1111-111111110001', 5, '2026-04-30T14:00:00+09:00', '2026-04-30T16:30:00+09:00'),
  ('9f7911d7-7c68-4c4d-9d66-8df46e0f36fb', '00000000-0000-0000-0000-000000000105', '신규 가져오기 직후 수색 구역 미설정 사건', 'OPEN', '2026-05-04T09:00:00+09:00', NULL, NULL, 1, '2026-05-04T09:00:00+09:00', '2026-05-04T09:00:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  source_incident_id = EXCLUDED.source_incident_id,
  title = EXCLUDED.title,
  status = EXCLUDED.status,
  opened_at = EXCLUDED.opened_at,
  closed_at = EXCLUDED.closed_at,
  closed_by_account_id = EXCLUDED.closed_by_account_id,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

INSERT INTO missing_person (
  incident_id,
  display_name,
  photo_object_key,
  appearance_text,
  last_seen_location_text,
  last_seen_at,
  imported_at
)
VALUES
  ('8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '실종자 T2-01', 'mock-112/missing-person/test2-001.jpg', '회색 점퍼, 검은 운동화, 작은 배낭', '한강공원 3번 출입구 인근', '2026-05-01T07:35:00+09:00', '2026-05-01T08:10:00+09:00'),
  ('e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', '실종자 T2-02', 'mock-112/missing-person/test2-002.jpg', '남색 모자, 흰색 상의, 검은 바지', '외곽 골목 편의점 앞', '2026-05-01T10:50:00+09:00', '2026-05-01T11:20:00+09:00'),
  ('6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', '실종자 T2-03', 'mock-112/missing-person/test2-003.jpg', '검은 우비, 장화, 손전등 소지 가능', '하천 산책로 남쪽 입구', '2026-05-01T18:55:00+09:00', '2026-05-01T19:35:00+09:00'),
  ('bd57c6d4-a791-4617-b15b-40dd0a5137aa', '실종자 T2-04', 'mock-112/missing-person/test2-004.jpg', '녹색 외투, 흰 운동화', '버스터미널 승강장 주변', '2026-04-30T13:15:00+09:00', '2026-04-30T14:00:00+09:00'),
  ('9f7911d7-7c68-4c4d-9d66-8df46e0f36fb', '실종자 T2-05', 'mock-112/missing-person/test2-005.jpg', '회색 후드, 검은 바지, 흰 운동화', '지구대 접수 직후 위치 확인 중', '2026-05-04T08:25:00+09:00', '2026-05-04T09:00:00+09:00')
ON CONFLICT (incident_id) DO UPDATE SET
  display_name = EXCLUDED.display_name,
  photo_object_key = EXCLUDED.photo_object_key,
  appearance_text = EXCLUDED.appearance_text,
  last_seen_location_text = EXCLUDED.last_seen_location_text,
  last_seen_at = EXCLUDED.last_seen_at,
  imported_at = EXCLUDED.imported_at;

INSERT INTO incident_assignment (
  id,
  incident_id,
  account_id,
  incident_role,
  assigned_at,
  revoked_at,
  created_at,
  updated_at
)
VALUES
  ('05ed9d26-8bb3-4a2f-a71b-7334fd1dcf68', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-05-01T08:10:00+09:00', NULL, '2026-05-01T08:10:00+09:00', '2026-05-01T08:10:00+09:00'),
  ('3e0f37bb-7f07-438d-a4c6-8b82a20cfe12', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '11111111-1111-1111-1111-111111110004', 'MEMBER', '2026-05-01T08:15:00+09:00', NULL, '2026-05-01T08:15:00+09:00', '2026-05-01T08:15:00+09:00'),
  ('5528423e-0a4c-4e99-8611-f486ba982462', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '11111111-1111-1111-1111-111111110007', 'MEMBER', '2026-05-01T08:17:00+09:00', NULL, '2026-05-01T08:17:00+09:00', '2026-05-01T08:17:00+09:00'),
  ('17668414-0fac-49bd-9cb2-c7937774997f', 'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-05-01T11:20:00+09:00', NULL, '2026-05-01T11:20:00+09:00', '2026-05-01T11:20:00+09:00'),
  ('8c5228b1-60fe-41c4-9542-67084324a2ad', 'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', '11111111-1111-1111-1111-111111110004', 'MEMBER', '2026-05-01T11:25:00+09:00', '2026-05-01T12:10:00+09:00', '2026-05-01T11:25:00+09:00', '2026-05-01T12:10:00+09:00'),
  ('0ac6dcff-aa5a-4cd2-a478-66cfa3e98d7c', 'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', '11111111-1111-1111-1111-111111110007', 'MEMBER', '2026-05-01T12:12:00+09:00', NULL, '2026-05-01T12:12:00+09:00', '2026-05-01T12:12:00+09:00'),
  ('4f5a2fee-0343-4aaf-8774-17b5b95cab25', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', '11111111-1111-1111-1111-111111110001', 'INCIDENT_COMMANDER', '2026-05-01T19:35:00+09:00', NULL, '2026-05-01T19:35:00+09:00', '2026-05-01T19:35:00+09:00'),
  ('7bf3df3a-12b8-484f-bd1e-ed3420b00a30', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', '11111111-1111-1111-1111-111111110004', 'MEMBER', '2026-05-01T19:40:00+09:00', NULL, '2026-05-01T19:40:00+09:00', '2026-05-01T19:40:00+09:00'),
  ('e1d05f87-06a9-44d7-a05a-238afc8a09ab', 'bd57c6d4-a791-4617-b15b-40dd0a5137aa', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-04-30T14:00:00+09:00', NULL, '2026-04-30T14:00:00+09:00', '2026-04-30T14:00:00+09:00'),
  ('9ca330ea-1577-4711-a348-6252f852fc21', '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-05-04T09:00:00+09:00', NULL, '2026-05-04T09:00:00+09:00', '2026-05-04T09:00:00+09:00'),
  ('70000000-0000-0000-0000-000000000401', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '11111111-1111-1111-1111-111111110401', 'MEMBER', '2026-05-01T09:14:00+09:00', NULL, '2026-05-01T09:14:00+09:00', '2026-05-01T09:14:00+09:00'),
  ('70000000-0000-0000-0000-000000000402', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '11111111-1111-1111-1111-111111110402', 'MEMBER', '2026-05-01T09:16:00+09:00', '2026-05-01T10:00:00+09:00', '2026-05-01T09:16:00+09:00', '2026-05-01T10:00:00+09:00'),
  ('70000000-0000-0000-0000-000000000403', 'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', '11111111-1111-1111-1111-111111110403', 'MEMBER', '2026-05-01T11:45:00+09:00', NULL, '2026-05-01T11:45:00+09:00', '2026-05-01T11:45:00+09:00'),
  ('70000000-0000-0000-0000-000000000404', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', '11111111-1111-1111-1111-111111110404', 'MEMBER', '2026-05-01T20:34:00+09:00', NULL, '2026-05-01T20:34:00+09:00', '2026-05-01T20:34:00+09:00'),
  ('70000000-0000-0000-0000-000000000405', '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb', '11111111-1111-1111-1111-111111110406', 'MEMBER', '2026-05-04T09:05:00+09:00', NULL, '2026-05-04T09:05:00+09:00', '2026-05-04T09:05:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  account_id = EXCLUDED.account_id,
  incident_role = EXCLUDED.incident_role,
  revoked_at = EXCLUDED.revoked_at,
  updated_at = EXCLUDED.updated_at;

INSERT INTO operational_period (
  id,
  incident_id,
  sequence_number,
  status,
  reason,
  reason_memo,
  started_by_account_id,
  ended_by_account_id,
  started_at,
  ended_at,
  version,
  created_at,
  updated_at
)
VALUES
  ('4bfe3a19-1270-4924-b581-ef7c4fc7d101', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', 1, 'ENDED', 'INITIAL', '초기 한강공원 산책로 수색', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:10:00+09:00', '2026-05-01T09:05:00+09:00', 2, '2026-05-01T08:10:00+09:00', '2026-05-01T09:05:00+09:00'),
  ('6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', 2, 'ACTIVE', 'RE_SEARCH', '북측 산책로 재수색', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T09:10:00+09:00', NULL, 1, '2026-05-01T09:10:00+09:00', '2026-05-01T09:10:00+09:00'),
  ('b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', 'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', 1, 'ACTIVE', 'INITIAL', '골목 초동 수색', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T11:20:00+09:00', NULL, 1, '2026-05-01T11:20:00+09:00', '2026-05-01T11:20:00+09:00'),
  ('78a8df07-3248-4fa3-b2df-7d9b8cb36daa', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', 1, 'ENDED', 'INITIAL', '하천변 야간 1차 수색', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-05-01T19:35:00+09:00', '2026-05-01T20:25:00+09:00', 2, '2026-05-01T19:35:00+09:00', '2026-05-01T20:25:00+09:00'),
  ('fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', 2, 'ACTIVE', 'AREA_CHANGED', '하천 하류 방향 수색 범위 변경', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T20:30:00+09:00', NULL, 1, '2026-05-01T20:30:00+09:00', '2026-05-01T20:30:00+09:00'),
  ('a3a1c010-4437-4878-87ce-f0bd7841fe75', 'bd57c6d4-a791-4617-b15b-40dd0a5137aa', 1, 'ENDED', 'INITIAL', '터미널 주변 단기 수색 완료', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-04-30T14:00:00+09:00', '2026-04-30T16:30:00+09:00', 5, '2026-04-30T14:00:00+09:00', '2026-04-30T16:30:00+09:00'),
  ('f550b765-6216-46d1-8e58-ed0fb33ff97d', '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb', 1, 'ACTIVE', 'INITIAL', '사건 가져오기 직후 자동 생성된 초기 OP', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-04T09:00:00+09:00', NULL, 1, '2026-05-04T09:00:00+09:00', '2026-05-04T09:00:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  incident_id = EXCLUDED.incident_id,
  sequence_number = EXCLUDED.sequence_number,
  status = EXCLUDED.status,
  reason = EXCLUDED.reason,
  reason_memo = EXCLUDED.reason_memo,
  started_by_account_id = EXCLUDED.started_by_account_id,
  ended_by_account_id = EXCLUDED.ended_by_account_id,
  started_at = EXCLUDED.started_at,
  ended_at = EXCLUDED.ended_at,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

-- No search_area rows are inserted for operational_period f550b765-6216-46d1-8e58-ed0fb33ff97d.
-- That OP belongs to incident 9f7911d7-7c68-4c4d-9d66-8df46e0f36fb and represents the empty-area state immediately after import.

INSERT INTO search_area (
  id,
  operational_period_id,
  parent_search_area_id,
  name,
  area_level,
  geometry,
  status,
  version,
  created_by_account_id,
  created_at,
  updated_at
)
VALUES
  ('cb0847bb-e7b3-4c40-9371-d39f7084c331', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', NULL, '한강공원 초기 전체 구역', 'OVERALL', ST_GeomFromText('POLYGON((126.9300 37.5200,126.9480 37.5200,126.9480 37.5320,126.9300 37.5320,126.9300 37.5200))', 4326), 'COMPLETED', 2, '11111111-1111-1111-1111-111111110001', '2026-05-01T08:20:00+09:00', '2026-05-01T09:00:00+09:00'),
  ('8d648840-f577-45aa-9a56-048241f79078', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', 'cb0847bb-e7b3-4c40-9371-d39f7084c331', '한강공원 서측 부대 구역', 'UNIT', ST_GeomFromText('POLYGON((126.9320 37.5220,126.9390 37.5220,126.9390 37.5300,126.9320 37.5300,126.9320 37.5220))', 4326), 'COMPLETED', 2, '11111111-1111-1111-1111-111111110001', '2026-05-01T08:25:00+09:00', '2026-05-01T09:00:00+09:00'),
  ('99d45e5e-e73d-4491-9c72-15090c83b5d2', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', '8d648840-f577-45aa-9a56-048241f79078', '한강공원 서측 1팀 구역', 'TEAM', ST_GeomFromText('POLYGON((126.9325 37.5225,126.9360 37.5225,126.9360 37.5295,126.9325 37.5295,126.9325 37.5225))', 4326), 'COMPLETED', 2, '11111111-1111-1111-1111-111111110001', '2026-05-01T08:30:00+09:00', '2026-05-01T08:55:00+09:00'),
  ('7f9da87c-c7ff-4d6a-8492-2cbd54122e28', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', '8d648840-f577-45aa-9a56-048241f79078', '한강공원 서측 2팀 취소 구역', 'TEAM', ST_GeomFromText('POLYGON((126.9360 37.5225,126.9385 37.5225,126.9385 37.5295,126.9360 37.5295,126.9360 37.5225))', 4326), 'CANCELLED', 2, '11111111-1111-1111-1111-111111110001', '2026-05-01T08:32:00+09:00', '2026-05-01T08:50:00+09:00'),
  ('b3216bfd-68d4-4cc2-85f7-6a018e958da3', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', NULL, '한강공원 북측 재수색 전체 구역', 'OVERALL', ST_GeomFromText('POLYGON((126.9290 37.5200,126.9500 37.5200,126.9500 37.5340,126.9290 37.5340,126.9290 37.5200))', 4326), 'ACTIVE', 1, '11111111-1111-1111-1111-111111110001', '2026-05-01T09:12:00+09:00', '2026-05-01T09:12:00+09:00'),
  ('d9e40c94-6e2d-4477-967d-ce60066f5190', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', 'b3216bfd-68d4-4cc2-85f7-6a018e958da3', '한강공원 북측 부대 구역', 'UNIT', ST_GeomFromText('POLYGON((126.9340 37.5250,126.9460 37.5250,126.9460 37.5330,126.9340 37.5330,126.9340 37.5250))', 4326), 'ACTIVE', 1, '11111111-1111-1111-1111-111111110001', '2026-05-01T09:15:00+09:00', '2026-05-01T09:15:00+09:00'),
  ('d776e36f-dc2f-4578-bd30-53e2c2d604c3', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', 'd9e40c94-6e2d-4477-967d-ce60066f5190', '한강공원 북측 1팀 구역', 'TEAM', ST_GeomFromText('POLYGON((126.9350 37.5260,126.9400 37.5260,126.9400 37.5320,126.9350 37.5320,126.9350 37.5260))', 4326), 'ACTIVE', 1, '11111111-1111-1111-1111-111111110001', '2026-05-01T09:18:00+09:00', '2026-05-01T09:18:00+09:00'),
  ('f7399859-8b03-4235-adc7-3aac2351e8db', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', 'd9e40c94-6e2d-4477-967d-ce60066f5190', '한강공원 북측 완료 구역', 'TEAM', ST_GeomFromText('POLYGON((126.9400 37.5260,126.9450 37.5260,126.9450 37.5320,126.9400 37.5320,126.9400 37.5260))', 4326), 'COMPLETED', 2, '11111111-1111-1111-1111-111111110001', '2026-05-01T09:20:00+09:00', '2026-05-01T09:38:00+09:00'),
  ('f347ec76-80f2-43d2-bd86-9a497daf39e4', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', NULL, '외곽 골목 전체 구역', 'OVERALL', ST_GeomFromText('POLYGON((126.9650 37.5500,126.9780 37.5500,126.9780 37.5600,126.9650 37.5600,126.9650 37.5500))', 4326), 'ACTIVE', 1, '11111111-1111-1111-1111-111111110001', '2026-05-01T11:25:00+09:00', '2026-05-01T11:25:00+09:00'),
  ('cc7ebdf7-3650-4c4b-adb8-05efc4aa3395', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', 'f347ec76-80f2-43d2-bd86-9a497daf39e4', '외곽 골목 1팀 구역', 'TEAM', ST_GeomFromText('POLYGON((126.9670 37.5520,126.9750 37.5520,126.9750 37.5580,126.9670 37.5580,126.9670 37.5520))', 4326), 'ACTIVE', 1, '11111111-1111-1111-1111-111111110001', '2026-05-01T11:30:00+09:00', '2026-05-01T11:30:00+09:00'),
  ('4a2e5d7f-0aec-4cf0-b3b9-3b9d71c4b4fb', '78a8df07-3248-4fa3-b2df-7d9b8cb36daa', NULL, '하천변 1차 전체 구역', 'OVERALL', ST_GeomFromText('POLYGON((127.0100 37.5000,127.0250 37.5000,127.0250 37.5120,127.0100 37.5120,127.0100 37.5000))', 4326), 'COMPLETED', 2, '11111111-1111-1111-1111-111111110001', '2026-05-01T19:40:00+09:00', '2026-05-01T20:20:00+09:00'),
  ('f56f1dde-61c4-4ca3-81f6-69364ce3fa10', 'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', NULL, '하천 하류 변경 전체 구역', 'OVERALL', ST_GeomFromText('POLYGON((127.0180 37.4950,127.0350 37.4950,127.0350 37.5080,127.0180 37.5080,127.0180 37.4950))', 4326), 'ACTIVE', 1, '11111111-1111-1111-1111-111111110001', '2026-05-01T20:35:00+09:00', '2026-05-01T20:35:00+09:00'),
  ('ec4f8fd4-bb7d-41d0-8164-fbd34e830c85', 'a3a1c010-4437-4878-87ce-f0bd7841fe75', NULL, '터미널 종료 전체 구역', 'OVERALL', ST_GeomFromText('POLYGON((126.8900 37.4850,126.9050 37.4850,126.9050 37.4960,126.8900 37.4960,126.8900 37.4850))', 4326), 'COMPLETED', 5, '11111111-1111-1111-1111-111111110001', '2026-04-30T14:10:00+09:00', '2026-04-30T16:20:00+09:00');

INSERT INTO search_area_history (
  id,
  search_area_id,
  change_type,
  previous_status,
  next_status,
  previous_geometry,
  next_geometry,
  change_memo,
  changed_by_account_id,
  changed_at,
  created_at
)
VALUES
  ('d2abbd39-a497-4918-9a4f-67ae7d8e6712', 'cb0847bb-e7b3-4c40-9371-d39f7084c331', 'CREATED', NULL, 'COMPLETED', NULL, ST_GeomFromText('POLYGON((126.9300 37.5200,126.9480 37.5200,126.9480 37.5320,126.9300 37.5320,126.9300 37.5200))', 4326), '전체 구역 생성', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:20:00+09:00', '2026-05-01T08:20:00+09:00'),
  ('9b4ef4a2-1393-4c4e-9f93-ef4d380726a1', '7f9da87c-c7ff-4d6a-8492-2cbd54122e28', 'STATUS_CHANGED', 'ACTIVE', 'CANCELLED', NULL, NULL, '중복 할당으로 취소', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:50:00+09:00', '2026-05-01T08:50:00+09:00'),
  ('3a6040fa-efb2-45c5-953e-57c5a4aaf7fb', 'b3216bfd-68d4-4cc2-85f7-6a018e958da3', 'GEOMETRY_UPDATED', 'ACTIVE', 'ACTIVE', ST_GeomFromText('POLYGON((126.9300 37.5200,126.9480 37.5200,126.9480 37.5320,126.9300 37.5320,126.9300 37.5200))', 4326), ST_GeomFromText('POLYGON((126.9290 37.5200,126.9500 37.5200,126.9500 37.5340,126.9290 37.5340,126.9290 37.5200))', 4326), '재수색 범위 확장', '11111111-1111-1111-1111-111111110001', '2026-05-01T09:12:00+09:00', '2026-05-01T09:12:00+09:00'),
  ('f9ae9c8f-eb92-4f7b-907a-7c76f1ee7ad2', 'd9e40c94-6e2d-4477-967d-ce60066f5190', 'SPLIT', NULL, 'ACTIVE', NULL, ST_GeomFromText('POLYGON((126.9340 37.5250,126.9460 37.5250,126.9460 37.5330,126.9340 37.5330,126.9340 37.5250))', 4326), '부대 구역 분할', '11111111-1111-1111-1111-111111110001', '2026-05-01T09:15:00+09:00', '2026-05-01T09:15:00+09:00'),
  ('be07bb0f-045d-4b65-adf4-c4cf4e7c7999', 'f7399859-8b03-4235-adc7-3aac2351e8db', 'STATUS_CHANGED', 'ACTIVE', 'COMPLETED', NULL, NULL, '무전 보고에 따라 완료 처리', '11111111-1111-1111-1111-111111110001', '2026-05-01T09:38:00+09:00', '2026-05-01T09:38:00+09:00'),
  ('510763b0-1757-4343-8a9b-1045ef6d1c92', 'f56f1dde-61c4-4ca3-81f6-69364ce3fa10', 'CREATED', NULL, 'ACTIVE', NULL, ST_GeomFromText('POLYGON((127.0180 37.4950,127.0350 37.4950,127.0350 37.5080,127.0180 37.5080,127.0180 37.4950))', 4326), '하류 방향 변경 구역 생성', '11111111-1111-1111-1111-111111110001', '2026-05-01T20:35:00+09:00', '2026-05-01T20:35:00+09:00'),
  ('622376e1-2be4-431a-b45a-40fd60da5db7', 'ec4f8fd4-bb7d-41d0-8164-fbd34e830c85', 'STATUS_CHANGED', 'ACTIVE', 'COMPLETED', NULL, NULL, '사건 종료 전 전체 수색 완료 처리', '11111111-1111-1111-1111-111111110001', '2026-04-30T16:20:00+09:00', '2026-04-30T16:20:00+09:00');

INSERT INTO offline_package_manifest (
  id,
  incident_id,
  manifest_version,
  operational_period_id,
  overall_search_area_id,
  overall_search_area_version,
  manifest_hash,
  manifest_format_version,
  manifest_payload,
  expires_at,
  created_at,
  updated_at
)
VALUES
  (
    '2a4bcb66-433f-4db7-8b55-2011b6255101',
    '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01',
    1,
    '6389d8a2-0acf-42d5-b74f-2f24e32d1f59',
    'b3216bfd-68d4-4cc2-85f7-6a018e958da3',
    1,
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    1,
    '{"fixture":"test2","packageItems":[{"itemKey":"incident:8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01","itemType":"INCIDENT_META","status":"DOWNLOADED"},{"itemKey":"overall-search-area:b3216bfd-68d4-4cc2-85f7-6a018e958da3","itemType":"OVERALL_SEARCH_AREA","status":"DOWNLOADED"},{"itemKey":"tile-manifest:2a4bcb66-433f-4db7-8b55-2011b6255101","itemType":"TILE","status":"DOWNLOADED"}]}',
    '2026-05-02T09:10:00+09:00',
    '2026-05-01T09:20:00+09:00',
    '2026-05-01T09:40:00+09:00'
  ),
  (
    'f2667c7f-4a40-4c46-82be-c40c03fd5102',
    'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2',
    1,
    'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4',
    'f347ec76-80f2-43d2-bd86-9a497daf39e4',
    1,
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    1,
    '{"fixture":"test2","packageItems":[{"itemKey":"incident:e2f0dc65-5e14-4a4c-9bf6-93453443f7d2","itemType":"INCIDENT_META","status":"DOWNLOADED"},{"itemKey":"overall-search-area:f347ec76-80f2-43d2-bd86-9a497daf39e4","itemType":"OVERALL_SEARCH_AREA","status":"DOWNLOADED"},{"itemKey":"tile-manifest:f2667c7f-4a40-4c46-82be-c40c03fd5102","itemType":"TILE","status":"PENDING"}]}',
    '2026-05-02T11:20:00+09:00',
    '2026-05-01T11:30:00+09:00',
    '2026-05-01T12:12:00+09:00'
  ),
  (
    '3b52cbe0-e426-49ec-8cfb-071d16a75103',
    '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f',
    2,
    'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76',
    'f56f1dde-61c4-4ca3-81f6-69364ce3fa10',
    1,
    'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
    1,
    '{"fixture":"test2","packageItems":[{"itemKey":"incident:6a28ddad-6a5b-4b2b-a676-fac5c2a3656f","itemType":"INCIDENT_META","status":"DOWNLOADED"},{"itemKey":"overall-search-area:f56f1dde-61c4-4ca3-81f6-69364ce3fa10","itemType":"OVERALL_SEARCH_AREA","status":"DOWNLOADED"},{"itemKey":"tile-manifest:3b52cbe0-e426-49ec-8cfb-071d16a75103","itemType":"TILE","status":"FAILED"}]}',
    '2026-05-02T20:30:00+09:00',
    '2026-05-01T20:35:00+09:00',
    '2026-05-01T21:15:00+09:00'
  ),
  (
    '81cbe5bb-6abf-4cb5-ac1f-a8bce0c55104',
    'bd57c6d4-a791-4617-b15b-40dd0a5137aa',
    1,
    'a3a1c010-4437-4878-87ce-f0bd7841fe75',
    'ec4f8fd4-bb7d-41d0-8164-fbd34e830c85',
    5,
    '0000000000000000000000000000000000000000000000000000000000000000',
    1,
    '{"fixture":"test2","purged":true,"reason":"package_purged","packageItems":[]}',
    '2026-05-01T16:30:00+09:00',
    '2026-04-30T14:10:00+09:00',
    '2026-04-30T16:30:00+09:00'
  )
ON CONFLICT (id) DO UPDATE SET
  incident_id = EXCLUDED.incident_id,
  manifest_version = EXCLUDED.manifest_version,
  operational_period_id = EXCLUDED.operational_period_id,
  overall_search_area_id = EXCLUDED.overall_search_area_id,
  overall_search_area_version = EXCLUDED.overall_search_area_version,
  manifest_hash = EXCLUDED.manifest_hash,
  manifest_format_version = EXCLUDED.manifest_format_version,
  manifest_payload = EXCLUDED.manifest_payload,
  expires_at = EXCLUDED.expires_at,
  updated_at = EXCLUDED.updated_at;

INSERT INTO offline_package_installation (
  id,
  offline_package_manifest_id,
  police_phone_id,
  last_reported_by_account_id,
  status,
  total_item_count,
  completed_item_count,
  failed_item_count,
  failed_item_keys,
  last_error_code,
  last_reported_at,
  version,
  created_at,
  updated_at
)
VALUES
  ('11a834b6-84e2-4082-bc09-3011b6255101', '2a4bcb66-433f-4db7-8b55-2011b6255101', '00000000-0000-0000-0000-000000000101', '11111111-1111-1111-1111-111111110003', 'READY', 7, 7, 0, ARRAY[]::text[], NULL, '2026-05-01T09:40:00+09:00', 3, '2026-05-01T09:20:00+09:00', '2026-05-01T09:40:00+09:00'),
  ('11a834b6-84e2-4082-bc09-3011b6255102', '2a4bcb66-433f-4db7-8b55-2011b6255101', '00000000-0000-0000-0000-000000000205', '11111111-1111-1111-1111-111111110005', 'PARTIAL', 7, 5, 2, ARRAY['tile:osm-local:15:27925:12680','tile:osm-local:16:27925:12681']::text[], 'tile-checksum-mismatch', '2026-05-01T09:36:00+09:00', 2, '2026-05-01T09:22:00+09:00', '2026-05-01T09:36:00+09:00'),
  ('11a834b6-84e2-4082-bc09-3011b6255103', '2a4bcb66-433f-4db7-8b55-2011b6255101', '00000000-0000-0000-0000-000000000207', '11111111-1111-1111-1111-111111110007', 'STALE', 7, 7, 0, ARRAY[]::text[], NULL, '2026-05-01T09:25:00+09:00', 4, '2026-05-01T09:24:00+09:00', '2026-05-01T09:38:00+09:00'),
  ('22a834b6-84e2-4082-bc09-3011b6255101', 'f2667c7f-4a40-4c46-82be-c40c03fd5102', '00000000-0000-0000-0000-000000000207', '11111111-1111-1111-1111-111111110007', 'DOWNLOADING', 7, 3, 0, ARRAY[]::text[], NULL, '2026-05-01T12:00:00+09:00', 1, '2026-05-01T11:35:00+09:00', '2026-05-01T12:00:00+09:00'),
  ('33a834b6-84e2-4082-bc09-3011b6255101', '3b52cbe0-e426-49ec-8cfb-071d16a75103', '00000000-0000-0000-0000-000000000205', '11111111-1111-1111-1111-111111110005', 'FAILED', 7, 4, 3, ARRAY['tile:osm-local:15:27960:12720','tile:osm-local:16:27925:12680','tile:osm-local:16:27926:12681']::text[], 'tile_unavailable', '2026-05-01T21:00:00+09:00', 5, '2026-05-01T20:40:00+09:00', '2026-05-01T21:00:00+09:00'),
  ('44a834b6-84e2-4082-bc09-3011b6255101', '81cbe5bb-6abf-4cb5-ac1f-a8bce0c55104', '00000000-0000-0000-0000-000000000101', NULL, 'PURGED', 0, 0, 0, NULL, NULL, NULL, 6, '2026-04-30T14:10:00+09:00', '2026-04-30T16:30:00+09:00'),
  ('55a834b6-84e2-4082-bc09-3011b6255401', '2a4bcb66-433f-4db7-8b55-2011b6255101', '00000000-0000-0000-0000-000000000401', '11111111-1111-1111-1111-111111110401', 'READY', 9, 9, 0, ARRAY[]::text[], NULL, '2026-05-01T09:44:00+09:00', 2, '2026-05-01T09:22:00+09:00', '2026-05-01T09:44:00+09:00'),
  ('55a834b6-84e2-4082-bc09-3011b6255402', '2a4bcb66-433f-4db7-8b55-2011b6255101', '00000000-0000-0000-0000-000000000402', '11111111-1111-1111-1111-111111110402', 'PARTIAL', 9, 6, 1, ARRAY['tile:osm-local:15:27924:12682']::text[], 'network_timeout', '2026-05-01T09:32:00+09:00', 3, '2026-05-01T09:24:00+09:00', '2026-05-01T09:32:00+09:00'),
  ('66a834b6-84e2-4082-bc09-3011b6255403', 'f2667c7f-4a40-4c46-82be-c40c03fd5102', '00000000-0000-0000-0000-000000000403', '11111111-1111-1111-1111-111111110403', 'READY', 8, 8, 0, ARRAY[]::text[], NULL, '2026-05-01T11:58:00+09:00', 2, '2026-05-01T11:35:00+09:00', '2026-05-01T11:58:00+09:00'),
  ('77a834b6-84e2-4082-bc09-3011b6255404', '3b52cbe0-e426-49ec-8cfb-071d16a75103', '00000000-0000-0000-0000-000000000404', '11111111-1111-1111-1111-111111110404', 'DOWNLOADING', 8, 2, 0, ARRAY[]::text[], NULL, '2026-05-01T20:55:00+09:00', 1, '2026-05-01T20:40:00+09:00', '2026-05-01T20:55:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  offline_package_manifest_id = EXCLUDED.offline_package_manifest_id,
  police_phone_id = EXCLUDED.police_phone_id,
  last_reported_by_account_id = EXCLUDED.last_reported_by_account_id,
  status = EXCLUDED.status,
  total_item_count = EXCLUDED.total_item_count,
  completed_item_count = EXCLUDED.completed_item_count,
  failed_item_count = EXCLUDED.failed_item_count,
  failed_item_keys = EXCLUDED.failed_item_keys,
  last_error_code = EXCLUDED.last_error_code,
  last_reported_at = EXCLUDED.last_reported_at,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

INSERT INTO search_area_assignment (
  id,
  search_area_id,
  assigned_account_id,
  assigned_by_account_id,
  assigned_at,
  revoked_at,
  status,
  memo,
  created_at,
  updated_at
)
VALUES
  ('253fc374-9451-4cfd-a380-5877693cce2c', '99d45e5e-e73d-4491-9c72-15090c83b5d2', '11111111-1111-1111-1111-111111110004', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:30:00+09:00', NULL, 'ACTIVE', '서측 1팀 완료 구역', '2026-05-01T08:30:00+09:00', '2026-05-01T08:55:00+09:00'),
  ('e5b9f762-3f55-4099-93c6-b1b56977d296', '7f9da87c-c7ff-4d6a-8492-2cbd54122e28', '11111111-1111-1111-1111-111111110007', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:32:00+09:00', '2026-05-01T08:50:00+09:00', 'CANCELLED', '중복 구역으로 할당 취소', '2026-05-01T08:32:00+09:00', '2026-05-01T08:50:00+09:00'),
  ('729fc953-f2a7-4ec5-b7b4-eb58ce5ca367', 'd776e36f-dc2f-4578-bd30-53e2c2d604c3', '11111111-1111-1111-1111-111111110004', '11111111-1111-1111-1111-111111110001', '2026-05-01T09:18:00+09:00', NULL, 'ACTIVE', '북측 1팀 진행 중', '2026-05-01T09:18:00+09:00', '2026-05-01T09:18:00+09:00'),
  ('a1fa2930-eb1d-46d8-b8ee-dac5effd701d', 'f7399859-8b03-4235-adc7-3aac2351e8db', '11111111-1111-1111-1111-111111110007', '11111111-1111-1111-1111-111111110001', '2026-05-01T09:20:00+09:00', NULL, 'ACTIVE', '북측 완료 처리된 구역', '2026-05-01T09:20:00+09:00', '2026-05-01T09:38:00+09:00'),
  ('6c0d2118-ecae-49d3-aabc-d0f65e0e2024', 'cc7ebdf7-3650-4c4b-adb8-05efc4aa3395', '11111111-1111-1111-1111-111111110007', '11111111-1111-1111-1111-111111110001', '2026-05-01T11:30:00+09:00', NULL, 'ACTIVE', '골목 내부 확인 담당', '2026-05-01T11:30:00+09:00', '2026-05-01T11:30:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  assigned_account_id = EXCLUDED.assigned_account_id,
  assigned_by_account_id = EXCLUDED.assigned_by_account_id,
  revoked_at = EXCLUDED.revoked_at,
  status = EXCLUDED.status,
  memo = EXCLUDED.memo,
  updated_at = EXCLUDED.updated_at;

INSERT INTO duty_shift (
  id,
  operational_period_id,
  incident_assignment_id,
  police_phone_id,
  status,
  started_by_account_id,
  ended_by_account_id,
  started_at,
  ended_at,
  version,
  created_at,
  updated_at
)
VALUES
  ('10000000-0000-0000-0000-000000000101', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', '3e0f37bb-7f07-438d-a4c6-8b82a20cfe12', '00000000-0000-0000-0000-000000000205', 'ENDED', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:20:00+09:00', '2026-05-01T09:05:00+09:00', 2, '2026-05-01T08:20:00+09:00', '2026-05-01T09:05:00+09:00'),
  ('10000000-0000-0000-0000-000000000102', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', '5528423e-0a4c-4e99-8611-f486ba982462', '00000000-0000-0000-0000-000000000207', 'ENDED', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:24:00+09:00', '2026-05-01T09:00:00+09:00', 2, '2026-05-01T08:24:00+09:00', '2026-05-01T09:00:00+09:00'),
  ('10000000-0000-0000-0000-000000000201', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '3e0f37bb-7f07-438d-a4c6-8b82a20cfe12', '00000000-0000-0000-0000-000000000205', 'ACTIVE', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T09:16:00+09:00', NULL, 3, '2026-05-01T09:16:00+09:00', '2026-05-01T10:10:00+09:00'),
  ('10000000-0000-0000-0000-000000000202', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '5528423e-0a4c-4e99-8611-f486ba982462', '00000000-0000-0000-0000-000000000207', 'ACTIVE', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T09:20:00+09:00', NULL, 2, '2026-05-01T09:20:00+09:00', '2026-05-01T10:05:00+09:00'),
  ('10000000-0000-0000-0000-000000000301', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', '0ac6dcff-aa5a-4cd2-a478-66cfa3e98d7c', '00000000-0000-0000-0000-000000000207', 'ACTIVE', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T11:24:00+09:00', NULL, 1, '2026-05-01T11:24:00+09:00', '2026-05-01T12:02:00+09:00'),
  ('10000000-0000-0000-0000-000000000401', '78a8df07-3248-4fa3-b2df-7d9b8cb36daa', '7bf3df3a-12b8-484f-bd1e-ed3420b00a30', '00000000-0000-0000-0000-000000000205', 'ENDED', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-05-01T19:05:00+09:00', '2026-05-01T19:52:00+09:00', 2, '2026-05-01T19:05:00+09:00', '2026-05-01T19:52:00+09:00'),
  ('10000000-0000-0000-0000-000000000402', 'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', '7bf3df3a-12b8-484f-bd1e-ed3420b00a30', '00000000-0000-0000-0000-000000000205', 'ACTIVE', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T20:32:00+09:00', NULL, 1, '2026-05-01T20:32:00+09:00', '2026-05-01T21:05:00+09:00'),
  ('10000000-0000-0000-0000-000000000501', 'a3a1c010-4437-4878-87ce-f0bd7841fe75', 'e1d05f87-06a9-44d7-a05a-238afc8a09ab', '00000000-0000-0000-0000-000000000101', 'ENDED', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-04-30T14:15:00+09:00', '2026-04-30T16:20:00+09:00', 4, '2026-04-30T14:15:00+09:00', '2026-04-30T16:20:00+09:00'),
  ('10000000-0000-0000-0000-000000000601', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '70000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000401', 'ACTIVE', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T09:24:00+09:00', NULL, 2, '2026-05-01T09:24:00+09:00', '2026-05-01T10:08:00+09:00'),
  ('10000000-0000-0000-0000-000000000602', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '70000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000402', 'ENDED', '11111111-1111-1111-1111-111111110001', '11111111-1111-1111-1111-111111110001', '2026-05-01T09:26:00+09:00', '2026-05-01T10:00:00+09:00', 2, '2026-05-01T09:26:00+09:00', '2026-05-01T10:00:00+09:00'),
  ('10000000-0000-0000-0000-000000000603', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', '70000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000403', 'ACTIVE', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T11:46:00+09:00', NULL, 1, '2026-05-01T11:46:00+09:00', '2026-05-01T12:05:00+09:00'),
  ('10000000-0000-0000-0000-000000000604', 'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', '70000000-0000-0000-0000-000000000404', '00000000-0000-0000-0000-000000000404', 'ACTIVE', '11111111-1111-1111-1111-111111110001', NULL, '2026-05-01T20:36:00+09:00', NULL, 1, '2026-05-01T20:36:00+09:00', '2026-05-01T21:12:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  operational_period_id = EXCLUDED.operational_period_id,
  incident_assignment_id = EXCLUDED.incident_assignment_id,
  police_phone_id = EXCLUDED.police_phone_id,
  status = EXCLUDED.status,
  started_by_account_id = EXCLUDED.started_by_account_id,
  ended_by_account_id = EXCLUDED.ended_by_account_id,
  started_at = EXCLUDED.started_at,
  ended_at = EXCLUDED.ended_at,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

INSERT INTO search_path (
  id,
  duty_shift_id,
  status,
  started_at,
  ended_at,
  geometry,
  version,
  created_at,
  updated_at
)
VALUES
  ('20000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000101', 'ENDED', '2026-05-01T08:28:00+09:00', '2026-05-01T08:58:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(126.93210 37.52220,126.93320 37.52270,126.93460 37.52310,126.93570 37.52380)'), 4326), 2, '2026-05-01T08:28:00+09:00', '2026-05-01T08:58:00+09:00'),
  ('20000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000102', 'ENDED', '2026-05-01T08:30:00+09:00', '2026-05-01T08:54:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(126.93600 37.52390,126.93680 37.52430,126.93760 37.52490)'), 4326), 2, '2026-05-01T08:30:00+09:00', '2026-05-01T08:54:00+09:00'),
  ('20000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000201', 'RECORDING', '2026-05-01T09:24:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.93510 37.52610,126.93620 37.52640,126.93710 37.52670)'), 4326), 3, '2026-05-01T09:24:00+09:00', '2026-05-01T10:08:00+09:00'),
  ('20000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000202', 'ENDED', '2026-05-01T09:28:00+09:00', '2026-05-01T09:55:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(126.94040 37.52680,126.94120 37.52710,126.94210 37.52760)'), 4326), 2, '2026-05-01T09:28:00+09:00', '2026-05-01T09:55:00+09:00'),
  ('20000000-0000-0000-0000-000000000301', '10000000-0000-0000-0000-000000000301', 'RECORDING', '2026-05-01T11:36:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.96810 37.55210,126.96890 37.55260,126.96980 37.55310)'), 4326), 1, '2026-05-01T11:36:00+09:00', '2026-05-01T12:01:00+09:00'),
  ('20000000-0000-0000-0000-000000000401', '10000000-0000-0000-0000-000000000401', 'ENDED', '2026-05-01T19:10:00+09:00', '2026-05-01T19:45:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(127.01320 37.50420,127.01400 37.50460,127.01460 37.50510)'), 4326), 2, '2026-05-01T19:10:00+09:00', '2026-05-01T19:45:00+09:00'),
  ('20000000-0000-0000-0000-000000000402', '10000000-0000-0000-0000-000000000402', 'RECORDING', '2026-05-01T20:38:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(127.02610 37.50010,127.02680 37.50060,127.02730 37.50100)'), 4326), 1, '2026-05-01T20:38:00+09:00', '2026-05-01T21:03:00+09:00'),
  ('20000000-0000-0000-0000-000000000501', '10000000-0000-0000-0000-000000000501', 'ENDED', '2026-04-30T14:22:00+09:00', '2026-04-30T16:05:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(126.89210 37.48810,126.89310 37.48880,126.89440 37.48920)'), 4326), 4, '2026-04-30T14:22:00+09:00', '2026-04-30T16:05:00+09:00'),
  ('20000000-0000-0000-0000-000000000601', '10000000-0000-0000-0000-000000000601', 'RECORDING', '2026-05-01T09:30:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.93520 37.52620,126.93610 37.52680,126.93730 37.52710,126.93820 37.52770)'), 4326), 2, '2026-05-01T09:30:00+09:00', '2026-05-01T10:08:00+09:00'),
  ('20000000-0000-0000-0000-000000000602', '10000000-0000-0000-0000-000000000602', 'ENDED', '2026-05-01T09:30:00+09:00', '2026-05-01T09:58:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(126.94040 37.52640,126.94180 37.52710,126.94300 37.52780)'), 4326), 2, '2026-05-01T09:30:00+09:00', '2026-05-01T09:58:00+09:00'),
  ('20000000-0000-0000-0000-000000000603', '10000000-0000-0000-0000-000000000603', 'RECORDING', '2026-05-01T11:50:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.96840 37.55240,126.96980 37.55300,126.97120 37.55370)'), 4326), 1, '2026-05-01T11:50:00+09:00', '2026-05-01T12:05:00+09:00'),
  ('20000000-0000-0000-0000-000000000604', '10000000-0000-0000-0000-000000000604', 'RECORDING', '2026-05-01T20:42:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(127.02030 37.49610,127.02140 37.49690,127.02280 37.49750)'), 4326), 1, '2026-05-01T20:42:00+09:00', '2026-05-01T21:12:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  duty_shift_id = EXCLUDED.duty_shift_id,
  status = EXCLUDED.status,
  started_at = EXCLUDED.started_at,
  ended_at = EXCLUDED.ended_at,
  geometry = EXCLUDED.geometry,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

INSERT INTO search_path_segment (
  id,
  search_path_id,
  movement_type,
  movement_type_source,
  geometry,
  started_at,
  ended_at,
  corrected_by_account_id,
  corrected_at,
  version,
  created_at,
  updated_at
)
VALUES
  ('21000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000101', 'FOOT', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(126.93210 37.52220,126.93320 37.52270)'), 4326), '2026-05-01T08:28:00+09:00', '2026-05-01T08:40:00+09:00', NULL, NULL, 1, '2026-05-01T08:28:00+09:00', '2026-05-01T08:40:00+09:00'),
  ('21000000-0000-0000-0000-000000000102', '20000000-0000-0000-0000-000000000101', 'VEHICLE', 'MANUAL', ST_SetSRID(ST_GeomFromText('LINESTRING(126.93320 37.52270,126.93460 37.52310,126.93570 37.52380)'), 4326), '2026-05-01T08:40:00+09:00', '2026-05-01T08:58:00+09:00', '11111111-1111-1111-1111-111111110001', '2026-05-01T08:59:00+09:00', 2, '2026-05-01T08:40:00+09:00', '2026-05-01T08:59:00+09:00'),
  ('21000000-0000-0000-0000-000000000201', '20000000-0000-0000-0000-000000000201', 'FOOT', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(126.93510 37.52610,126.93620 37.52640,126.93710 37.52670)'), 4326), '2026-05-01T09:24:00+09:00', '2026-05-01T10:08:00+09:00', NULL, NULL, 3, '2026-05-01T09:24:00+09:00', '2026-05-01T10:08:00+09:00'),
  ('21000000-0000-0000-0000-000000000301', '20000000-0000-0000-0000-000000000301', 'UNKNOWN', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(126.96810 37.55210,126.96890 37.55260,126.96980 37.55310)'), 4326), '2026-05-01T11:36:00+09:00', '2026-05-01T12:01:00+09:00', NULL, NULL, 1, '2026-05-01T11:36:00+09:00', '2026-05-01T12:01:00+09:00'),
  ('21000000-0000-0000-0000-000000000501', '20000000-0000-0000-0000-000000000501', 'VEHICLE', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(126.89210 37.48810,126.89310 37.48880,126.89440 37.48920)'), 4326), '2026-04-30T14:22:00+09:00', '2026-04-30T16:05:00+09:00', NULL, NULL, 4, '2026-04-30T14:22:00+09:00', '2026-04-30T16:05:00+09:00'),
  ('21000000-0000-0000-0000-000000000601', '20000000-0000-0000-0000-000000000601', 'FOOT', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(126.93520 37.52620,126.93610 37.52680,126.93730 37.52710)'), 4326), '2026-05-01T09:30:00+09:00', '2026-05-01T09:52:00+09:00', NULL, NULL, 1, '2026-05-01T09:30:00+09:00', '2026-05-01T09:52:00+09:00'),
  ('21000000-0000-0000-0000-000000000602', '20000000-0000-0000-0000-000000000601', 'FOOT', 'MANUAL', ST_SetSRID(ST_GeomFromText('LINESTRING(126.93730 37.52710,126.93820 37.52770)'), 4326), '2026-05-01T09:52:00+09:00', '2026-05-01T10:08:00+09:00', '11111111-1111-1111-1111-111111110001', '2026-05-01T10:09:00+09:00', 2, '2026-05-01T09:52:00+09:00', '2026-05-01T10:09:00+09:00'),
  ('21000000-0000-0000-0000-000000000603', '20000000-0000-0000-0000-000000000602', 'VEHICLE', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(126.94040 37.52640,126.94180 37.52710,126.94300 37.52780)'), 4326), '2026-05-01T09:30:00+09:00', '2026-05-01T09:58:00+09:00', NULL, NULL, 1, '2026-05-01T09:30:00+09:00', '2026-05-01T09:58:00+09:00'),
  ('21000000-0000-0000-0000-000000000604', '20000000-0000-0000-0000-000000000603', 'FOOT', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(126.96840 37.55240,126.96980 37.55300,126.97120 37.55370)'), 4326), '2026-05-01T11:50:00+09:00', '2026-05-01T12:05:00+09:00', NULL, NULL, 1, '2026-05-01T11:50:00+09:00', '2026-05-01T12:05:00+09:00'),
  ('21000000-0000-0000-0000-000000000605', '20000000-0000-0000-0000-000000000604', 'UNKNOWN', 'AUTO', ST_SetSRID(ST_GeomFromText('LINESTRING(127.02030 37.49610,127.02140 37.49690,127.02280 37.49750)'), 4326), '2026-05-01T20:42:00+09:00', '2026-05-01T21:12:00+09:00', NULL, NULL, 1, '2026-05-01T20:42:00+09:00', '2026-05-01T21:12:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  search_path_id = EXCLUDED.search_path_id,
  movement_type = EXCLUDED.movement_type,
  movement_type_source = EXCLUDED.movement_type_source,
  geometry = EXCLUDED.geometry,
  started_at = EXCLUDED.started_at,
  ended_at = EXCLUDED.ended_at,
  corrected_by_account_id = EXCLUDED.corrected_by_account_id,
  corrected_at = EXCLUDED.corrected_at,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

INSERT INTO search_path_excluded_point (
  id,
  search_path_id,
  point_id,
  reason,
  client_ts,
  created_at,
  updated_at
)
VALUES
  ('22000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000101', 'gps-west-0014', 'low_accuracy', '2026-05-01T08:42:12+09:00', '2026-05-01T08:42:20+09:00', '2026-05-01T08:42:20+09:00'),
  ('22000000-0000-0000-0000-000000000201', '20000000-0000-0000-0000-000000000201', 'gps-north-0041', 'distance_jump', '2026-05-01T09:50:30+09:00', '2026-05-01T09:50:40+09:00', '2026-05-01T09:50:40+09:00'),
  ('22000000-0000-0000-0000-000000000301', '20000000-0000-0000-0000-000000000301', 'gps-alley-0007', 'clock_skew', '2026-05-01T11:44:05+09:00', '2026-05-01T11:44:10+09:00', '2026-05-01T11:44:10+09:00'),
  ('22000000-0000-0000-0000-000000000601', '20000000-0000-0000-0000-000000000601', 'gps-test2-river-team-0009', 'low_accuracy', '2026-05-01T09:41:05+09:00', '2026-05-01T09:41:12+09:00', '2026-05-01T09:41:12+09:00'),
  ('22000000-0000-0000-0000-000000000602', '20000000-0000-0000-0000-000000000604', 'gps-test2-night-0018', 'distance_jump', '2026-05-01T20:58:45+09:00', '2026-05-01T20:58:50+09:00', '2026-05-01T20:58:50+09:00')
ON CONFLICT (id) DO UPDATE SET
  search_path_id = EXCLUDED.search_path_id,
  point_id = EXCLUDED.point_id,
  reason = EXCLUDED.reason,
  client_ts = EXCLUDED.client_ts,
  updated_at = EXCLUDED.updated_at;

INSERT INTO marker (
  id,
  incident_id,
  operational_period_id,
  duty_shift_id,
  marker_type,
  support_request_type,
  location,
  memo,
  occurred_at,
  created_by_account_id,
  police_phone_id,
  marker_source,
  status,
  version,
  created_at,
  updated_at
)
VALUES
  ('30000000-0000-0000-0000-000000000101', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', '10000000-0000-0000-0000-000000000101', 'CLUE', NULL, ST_SetSRID(ST_MakePoint(126.93320, 37.52270), 4326), 'bag found near west stair', '2026-05-01T08:39:00+09:00', '11111111-1111-1111-1111-111111110004', '00000000-0000-0000-0000-000000000205', 'MOCK_SEED', 'ACTIVE', 1, '2026-05-01T08:39:00+09:00', '2026-05-01T08:39:00+09:00'),
  ('30000000-0000-0000-0000-000000000102', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', '10000000-0000-0000-0000-000000000102', 'FIELD_CONDITION', NULL, ST_SetSRID(ST_MakePoint(126.93710, 37.52460), 4326), 'closed gate, checked from outside', '2026-05-01T08:46:00+09:00', '11111111-1111-1111-1111-111111110007', '00000000-0000-0000-0000-000000000207', 'MOCK_SEED', 'UPDATED', 2, '2026-05-01T08:46:00+09:00', '2026-05-01T08:52:00+09:00'),
  ('30000000-0000-0000-0000-000000000201', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '10000000-0000-0000-0000-000000000201', 'NOTE', NULL, ST_SetSRID(ST_MakePoint(126.93660, 37.52650), 4326), 'north team met resident witness', '2026-05-01T09:42:00+09:00', '11111111-1111-1111-1111-111111110004', '00000000-0000-0000-0000-000000000205', 'APP', 'ACTIVE', 1, '2026-05-01T09:42:00+09:00', '2026-05-01T09:42:00+09:00'),
  ('30000000-0000-0000-0000-000000000202', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '10000000-0000-0000-0000-000000000202', 'SUPPORT_REQUEST', 'DRONE', ST_SetSRID(ST_MakePoint(126.94160, 37.52740), 4326), 'request aerial check over roof line', '2026-05-01T09:48:00+09:00', '11111111-1111-1111-1111-111111110007', '00000000-0000-0000-0000-000000000207', 'APP', 'ACTIVE', 1, '2026-05-01T09:48:00+09:00', '2026-05-01T09:48:00+09:00'),
  ('30000000-0000-0000-0000-000000000301', 'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', '10000000-0000-0000-0000-000000000301', 'SUPPORT_REQUEST', 'POLICE_DOG', ST_SetSRID(ST_MakePoint(126.96910, 37.55280), 4326), 'dog team requested for alley cluster', '2026-05-01T11:52:00+09:00', '11111111-1111-1111-1111-111111110007', '00000000-0000-0000-0000-000000000207', 'APP', 'ACTIVE', 1, '2026-05-01T11:52:00+09:00', '2026-05-01T11:52:00+09:00'),
  ('30000000-0000-0000-0000-000000000401', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', '78a8df07-3248-4fa3-b2df-7d9b8cb36daa', '10000000-0000-0000-0000-000000000401', 'PERSON_FOUND', NULL, ST_SetSRID(ST_MakePoint(127.01410, 37.50480), 4326), 'person found and handed over to command', '2026-05-01T19:44:00+09:00', '11111111-1111-1111-1111-111111110004', '00000000-0000-0000-0000-000000000205', 'APP', 'ACTIVE', 1, '2026-05-01T19:44:00+09:00', '2026-05-01T19:44:00+09:00'),
  ('30000000-0000-0000-0000-000000000402', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', 'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', '10000000-0000-0000-0000-000000000402', 'FIELD_CONDITION', NULL, ST_SetSRID(ST_MakePoint(127.02690, 37.50070), 4326), 'steep slope, proceed on foot only', '2026-05-01T20:52:00+09:00', '11111111-1111-1111-1111-111111110004', '00000000-0000-0000-0000-000000000205', 'APP', 'ACTIVE', 1, '2026-05-01T20:52:00+09:00', '2026-05-01T20:52:00+09:00'),
  ('30000000-0000-0000-0000-000000000501', 'bd57c6d4-a791-4617-b15b-40dd0a5137aa', 'a3a1c010-4437-4878-87ce-f0bd7841fe75', '10000000-0000-0000-0000-000000000501', 'NOTE', NULL, ST_SetSRID(ST_MakePoint(126.89360, 37.48900), 4326), 'closed incident archive note', '2026-04-30T15:12:00+09:00', '11111111-1111-1111-1111-111111110003', '00000000-0000-0000-0000-000000000101', 'MOCK_SEED', 'DELETED', 2, '2026-04-30T15:12:00+09:00', '2026-04-30T16:10:00+09:00'),
  ('30000000-0000-0000-0000-000000000601', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '10000000-0000-0000-0000-000000000601', 'CLUE', NULL, ST_SetSRID(ST_MakePoint(126.93610, 37.52680), 4326), 'fresh footprint near north fence', '2026-05-01T09:46:00+09:00', '11111111-1111-1111-1111-111111110401', '00000000-0000-0000-0000-000000000401', 'APP', 'ACTIVE', 1, '2026-05-01T09:46:00+09:00', '2026-05-01T09:46:00+09:00'),
  ('30000000-0000-0000-0000-000000000602', '8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '10000000-0000-0000-0000-000000000602', 'FIELD_CONDITION', NULL, ST_SetSRID(ST_MakePoint(126.94240, 37.52750), 4326), 'patrol car blocked by construction barrier', '2026-05-01T09:52:00+09:00', '11111111-1111-1111-1111-111111110402', '00000000-0000-0000-0000-000000000402', 'APP', 'UPDATED', 2, '2026-05-01T09:52:00+09:00', '2026-05-01T09:58:00+09:00'),
  ('30000000-0000-0000-0000-000000000603', 'e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', '10000000-0000-0000-0000-000000000603', 'NOTE', NULL, ST_SetSRID(ST_MakePoint(126.97030, 37.55320), 4326), 'support team checked rear entrance', '2026-05-01T12:01:00+09:00', '11111111-1111-1111-1111-111111110403', '00000000-0000-0000-0000-000000000403', 'APP', 'ACTIVE', 1, '2026-05-01T12:01:00+09:00', '2026-05-01T12:01:00+09:00'),
  ('30000000-0000-0000-0000-000000000604', '6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', 'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', '10000000-0000-0000-0000-000000000604', 'SUPPORT_REQUEST', 'DRONE', ST_SetSRID(ST_MakePoint(127.02190, 37.49710), 4326), 'night team requests thermal drone sweep', '2026-05-01T21:02:00+09:00', '11111111-1111-1111-1111-111111110404', '00000000-0000-0000-0000-000000000404', 'APP', 'ACTIVE', 1, '2026-05-01T21:02:00+09:00', '2026-05-01T21:02:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  incident_id = EXCLUDED.incident_id,
  operational_period_id = EXCLUDED.operational_period_id,
  duty_shift_id = EXCLUDED.duty_shift_id,
  marker_type = EXCLUDED.marker_type,
  support_request_type = EXCLUDED.support_request_type,
  location = EXCLUDED.location,
  memo = EXCLUDED.memo,
  occurred_at = EXCLUDED.occurred_at,
  created_by_account_id = EXCLUDED.created_by_account_id,
  police_phone_id = EXCLUDED.police_phone_id,
  marker_source = EXCLUDED.marker_source,
  status = EXCLUDED.status,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

INSERT INTO photo (
  id,
  marker_id,
  object_key,
  status,
  attached_at,
  content_type,
  size_bytes,
  width,
  height,
  checksum_sha256,
  captured_at,
  upload_url_expires_at,
  version,
  created_at,
  updated_at,
  deleted_at
)
VALUES
  ('31000000-0000-0000-0000-000000000101', '30000000-0000-0000-0000-000000000101', 'mock/test2/incident-01/clue-west-stair.jpg', 'ATTACHED', '2026-05-01T08:41:00+09:00', 'image/jpeg', 482104, 1280, 960, '1111111111111111111111111111111111111111111111111111111111111111', '2026-05-01T08:39:20+09:00', NULL, 1, '2026-05-01T08:40:00+09:00', '2026-05-01T08:41:00+09:00', NULL),
  ('31000000-0000-0000-0000-000000000202', '30000000-0000-0000-0000-000000000202', 'mock/test2/incident-01/drone-request-roof.jpg', 'PENDING_UPLOAD', NULL, 'image/jpeg', 318004, 1024, 768, '2222222222222222222222222222222222222222222222222222222222222222', '2026-05-01T09:48:30+09:00', '2026-05-01T10:48:30+09:00', 1, '2026-05-01T09:48:40+09:00', '2026-05-01T09:48:40+09:00', NULL),
  ('31000000-0000-0000-0000-000000000401', '30000000-0000-0000-0000-000000000401', 'mock/test2/incident-03/person-found.jpg', 'ATTACHED', '2026-05-01T19:46:00+09:00', 'image/jpeg', 552912, 1440, 1080, '3333333333333333333333333333333333333333333333333333333333333333', '2026-05-01T19:44:20+09:00', NULL, 1, '2026-05-01T19:45:00+09:00', '2026-05-01T19:46:00+09:00', NULL),
  ('31000000-0000-0000-0000-000000000601', '30000000-0000-0000-0000-000000000601', 'mock/test2/incident-01/footprint-north-fence.jpg', 'ATTACHED', '2026-05-01T09:47:00+09:00', 'image/jpeg', 421800, 1280, 960, '4444444444444444444444444444444444444444444444444444444444444444', '2026-05-01T09:46:10+09:00', NULL, 1, '2026-05-01T09:46:30+09:00', '2026-05-01T09:47:00+09:00', NULL),
  ('31000000-0000-0000-0000-000000000604', '30000000-0000-0000-0000-000000000604', 'mock/test2/incident-03/night-drone-request.jpg', 'PENDING_UPLOAD', NULL, 'image/jpeg', 390112, 1024, 768, '5555555555555555555555555555555555555555555555555555555555555555', '2026-05-01T21:02:30+09:00', '2026-05-01T22:02:30+09:00', 1, '2026-05-01T21:02:40+09:00', '2026-05-01T21:02:40+09:00', NULL)
ON CONFLICT (id) DO UPDATE SET
  marker_id = EXCLUDED.marker_id,
  object_key = EXCLUDED.object_key,
  status = EXCLUDED.status,
  attached_at = EXCLUDED.attached_at,
  content_type = EXCLUDED.content_type,
  size_bytes = EXCLUDED.size_bytes,
  width = EXCLUDED.width,
  height = EXCLUDED.height,
  checksum_sha256 = EXCLUDED.checksum_sha256,
  captured_at = EXCLUDED.captured_at,
  upload_url_expires_at = EXCLUDED.upload_url_expires_at,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at,
  deleted_at = EXCLUDED.deleted_at;

INSERT INTO marker_notification (
  id,
  marker_id,
  notification_type,
  recipient_rule,
  recipient_account_ids,
  recipient_police_phone_ids,
  notification_payload,
  status,
  version,
  created_at
)
VALUES
  ('32000000-0000-0000-0000-000000000202', '30000000-0000-0000-0000-000000000202', 'SUPPORT_REQUEST_CREATED', 'COMMANDERS_AND_FIELD_COMMANDERS', ARRAY['11111111-1111-1111-1111-111111110001']::uuid[], ARRAY[]::uuid[], '{"fixture":"test2","markerType":"SUPPORT_REQUEST","supportRequestType":"DRONE"}', 'SNAPSHOT_CREATED', 1, '2026-05-01T09:48:05+09:00'),
  ('32000000-0000-0000-0000-000000000301', '30000000-0000-0000-0000-000000000301', 'SUPPORT_REQUEST_CREATED', 'ALL_INCIDENT_ASSIGNED', ARRAY['11111111-1111-1111-1111-111111110001','11111111-1111-1111-1111-111111110007']::uuid[], ARRAY['00000000-0000-0000-0000-000000000207']::uuid[], '{"fixture":"test2","markerType":"SUPPORT_REQUEST","supportRequestType":"POLICE_DOG"}', 'SNAPSHOT_CREATED', 1, '2026-05-01T11:52:05+09:00'),
  ('32000000-0000-0000-0000-000000000401', '30000000-0000-0000-0000-000000000401', 'PERSON_FOUND', 'COMMANDERS_AND_FIELD_COMMANDERS', ARRAY['11111111-1111-1111-1111-111111110001']::uuid[], ARRAY[]::uuid[], '{"fixture":"test2","markerType":"PERSON_FOUND"}', 'SNAPSHOT_CREATED', 1, '2026-05-01T19:44:05+09:00'),
  ('32000000-0000-0000-0000-000000000601', '30000000-0000-0000-0000-000000000604', 'SUPPORT_REQUEST_CREATED', 'ALL_INCIDENT_ASSIGNED', ARRAY['11111111-1111-1111-1111-111111110001','11111111-1111-1111-1111-111111110404']::uuid[], ARRAY['00000000-0000-0000-0000-000000000404']::uuid[], '{"fixture":"test2","markerType":"SUPPORT_REQUEST","supportRequestType":"DRONE","source":"night-team"}', 'SNAPSHOT_CREATED', 1, '2026-05-01T21:02:05+09:00')
ON CONFLICT (id) DO UPDATE SET
  marker_id = EXCLUDED.marker_id,
  notification_type = EXCLUDED.notification_type,
  recipient_rule = EXCLUDED.recipient_rule,
  recipient_account_ids = EXCLUDED.recipient_account_ids,
  recipient_police_phone_ids = EXCLUDED.recipient_police_phone_ids,
  notification_payload = EXCLUDED.notification_payload,
  status = EXCLUDED.status,
  version = EXCLUDED.version;

INSERT INTO handover_memo (
  id,
  operational_period_id,
  memo_target_type,
  memo_target_id,
  content,
  created_by_account_id,
  duty_shift_id,
  status,
  version,
  created_at,
  updated_at
)
VALUES
  ('40000000-0000-0000-0000-000000000101', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', 'OPERATIONAL_PERIOD', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', 'OP1 west side completed, north side remains for next OP.', '11111111-1111-1111-1111-111111110001', NULL, 'ACTIVE', 1, '2026-05-01T09:04:00+09:00', '2026-05-01T09:04:00+09:00'),
  ('40000000-0000-0000-0000-000000000201', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', 'SEARCH_AREA', 'd776e36f-dc2f-4578-bd30-53e2c2d604c3', 'Keep one person near north alley entrance while drone request is pending.', '11111111-1111-1111-1111-111111110004', '10000000-0000-0000-0000-000000000201', 'ACTIVE', 1, '2026-05-01T09:56:00+09:00', '2026-05-01T09:56:00+09:00'),
  ('40000000-0000-0000-0000-000000000202', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', 'MARKER', '30000000-0000-0000-0000-000000000202', 'Drone support request was created from field terminal.', '11111111-1111-1111-1111-111111110007', '10000000-0000-0000-0000-000000000202', 'ACTIVE', 1, '2026-05-01T09:58:00+09:00', '2026-05-01T09:58:00+09:00'),
  ('40000000-0000-0000-0000-000000000301', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', 'DUTY_SHIFT', '10000000-0000-0000-0000-000000000301', 'Support car is checking alley cluster and waiting for dog team response.', '11111111-1111-1111-1111-111111110007', '10000000-0000-0000-0000-000000000301', 'ACTIVE', 1, '2026-05-01T12:00:00+09:00', '2026-05-01T12:00:00+09:00'),
  ('40000000-0000-0000-0000-000000000401', '78a8df07-3248-4fa3-b2df-7d9b8cb36daa', 'SEARCH_PATH', '20000000-0000-0000-0000-000000000401', 'Night OP1 route ended after person found marker.', '11111111-1111-1111-1111-111111110004', '10000000-0000-0000-0000-000000000401', 'ACTIVE', 1, '2026-05-01T19:50:00+09:00', '2026-05-01T19:50:00+09:00'),
  ('40000000-0000-0000-0000-000000000601', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', 'DUTY_SHIFT', '10000000-0000-0000-0000-000000000601', 'River team phone is active and uploaded a north fence clue photo.', '11111111-1111-1111-1111-111111110401', '10000000-0000-0000-0000-000000000601', 'ACTIVE', 1, '2026-05-01T10:06:00+09:00', '2026-05-01T10:06:00+09:00'),
  ('40000000-0000-0000-0000-000000000602', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', 'MARKER', '30000000-0000-0000-0000-000000000602', 'River car phone was revoked after the construction barrier report.', '11111111-1111-1111-1111-111111110402', '10000000-0000-0000-0000-000000000602', 'ACTIVE', 1, '2026-05-01T10:00:00+09:00', '2026-05-01T10:00:00+09:00'),
  ('40000000-0000-0000-0000-000000000604', 'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', 'MARKER', '30000000-0000-0000-0000-000000000604', 'Thermal drone request remains open for the night team.', '11111111-1111-1111-1111-111111110404', '10000000-0000-0000-0000-000000000604', 'ACTIVE', 1, '2026-05-01T21:05:00+09:00', '2026-05-01T21:05:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  operational_period_id = EXCLUDED.operational_period_id,
  memo_target_type = EXCLUDED.memo_target_type,
  memo_target_id = EXCLUDED.memo_target_id,
  content = EXCLUDED.content,
  created_by_account_id = EXCLUDED.created_by_account_id,
  duty_shift_id = EXCLUDED.duty_shift_id,
  status = EXCLUDED.status,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

INSERT INTO search_history_summary (
  id,
  operational_period_id,
  duty_shift_id,
  generation_status,
  content,
  source_data_hash,
  source_readiness,
  requested_by_account_id,
  generated_at,
  version,
  created_at,
  updated_at
)
VALUES
  ('50000000-0000-0000-0000-000000000101', '4bfe3a19-1270-4924-b581-ef7c4fc7d101', NULL, 'READY', 'OP1 summary: two duty shifts ended, west search area completed, one clue and one field condition marker recorded.', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'READY', '11111111-1111-1111-1111-111111110001', '2026-05-01T09:08:00+09:00', 1, '2026-05-01T09:07:00+09:00', '2026-05-01T09:08:00+09:00'),
  ('50000000-0000-0000-0000-000000000201', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', NULL, 'GENERATING', NULL, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'PENDING_SYNC', '11111111-1111-1111-1111-111111110001', NULL, 1, '2026-05-01T10:02:00+09:00', '2026-05-01T10:02:00+09:00'),
  ('50000000-0000-0000-0000-000000000202', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '10000000-0000-0000-0000-000000000201', 'READY', 'North team shift summary: route is recording, witness note marker added, one GPS point excluded for distance jump.', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'STALE', '11111111-1111-1111-1111-111111110001', '2026-05-01T10:05:00+09:00', 2, '2026-05-01T10:04:00+09:00', '2026-05-01T10:05:00+09:00'),
  ('50000000-0000-0000-0000-000000000301', 'b1c5e0ef-f662-4c0b-b6de-7e17dfd3e4b4', NULL, 'FAILED', 'Summary generation failed because terminal sync is incomplete.', 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', 'PENDING_SYNC', '11111111-1111-1111-1111-111111110001', NULL, 1, '2026-05-01T12:04:00+09:00', '2026-05-01T12:05:00+09:00'),
  ('50000000-0000-0000-0000-000000000401', '78a8df07-3248-4fa3-b2df-7d9b8cb36daa', '10000000-0000-0000-0000-000000000401', 'READY', 'Night OP1 shift summary: one vehicle route ended and one person found marker was recorded.', 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', 'READY', '11111111-1111-1111-1111-111111110001', '2026-05-01T19:55:00+09:00', 1, '2026-05-01T19:54:00+09:00', '2026-05-01T19:55:00+09:00'),
  ('50000000-0000-0000-0000-000000000601', '6389d8a2-0acf-42d5-b74f-2f24e32d1f59', '10000000-0000-0000-0000-000000000601', 'READY', 'River team summary: active phone, active route, one clue marker, one uploaded photo, one excluded low accuracy point.', 'ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', 'READY', '11111111-1111-1111-1111-111111110001', '2026-05-01T10:10:00+09:00', 1, '2026-05-01T10:09:00+09:00', '2026-05-01T10:10:00+09:00'),
  ('50000000-0000-0000-0000-000000000604', 'fcdd5f52-8f07-4e49-9a5d-e3231a1afe76', '10000000-0000-0000-0000-000000000604', 'GENERATING', NULL, '9999999999999999999999999999999999999999999999999999999999999999', 'PENDING_SYNC', '11111111-1111-1111-1111-111111110001', NULL, 1, '2026-05-01T21:13:00+09:00', '2026-05-01T21:13:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  operational_period_id = EXCLUDED.operational_period_id,
  duty_shift_id = EXCLUDED.duty_shift_id,
  generation_status = EXCLUDED.generation_status,
  content = EXCLUDED.content,
  source_data_hash = EXCLUDED.source_data_hash,
  source_readiness = EXCLUDED.source_readiness,
  requested_by_account_id = EXCLUDED.requested_by_account_id,
  generated_at = EXCLUDED.generated_at,
  version = EXCLUDED.version,
  updated_at = EXCLUDED.updated_at;

COMMIT;
