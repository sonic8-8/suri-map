-- Suri-Map frontend mock data set #2.
-- Account rows are not inserted here. Existing seed account IDs are referenced.
-- Includes one newly imported incident with an active OP but no search_area rows.

SET client_encoding = 'UTF8';
CREATE EXTENSION IF NOT EXISTS postgis;

BEGIN;

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
  ('8d5b4a0b-7f72-43c4-a5d8-7ff3be3d1c01', 'mock-test2-incident-001', '한강공원 산책로 실종자 수색', 'OPEN', '2026-05-01T08:10:00+09:00', NULL, NULL, 3, '2026-05-01T08:10:00+09:00', '2026-05-01T09:40:00+09:00'),
  ('e2f0dc65-5e14-4a4c-9bf6-93453443f7d2', 'mock-test2-incident-002', '도심 외곽 골목 실종자 수색', 'OPEN', '2026-05-01T11:20:00+09:00', NULL, NULL, 1, '2026-05-01T11:20:00+09:00', '2026-05-01T11:20:00+09:00'),
  ('6a28ddad-6a5b-4b2b-a676-fac5c2a3656f', 'mock-test2-incident-003', '하천변 야간 실종자 수색', 'OPEN', '2026-05-01T19:35:00+09:00', NULL, NULL, 4, '2026-05-01T19:35:00+09:00', '2026-05-01T21:15:00+09:00'),
  ('bd57c6d4-a791-4617-b15b-40dd0a5137aa', 'mock-test2-incident-004', '터미널 주변 단기 수색 종료 사건', 'CLOSED', '2026-04-30T14:00:00+09:00', '2026-04-30T16:30:00+09:00', '11111111-1111-1111-1111-111111110001', 5, '2026-04-30T14:00:00+09:00', '2026-04-30T16:30:00+09:00'),
  ('9f7911d7-7c68-4c4d-9d66-8df46e0f36fb', 'mock-test2-incident-005', '신규 가져오기 직후 수색 구역 미설정 사건', 'OPEN', '2026-05-04T09:00:00+09:00', NULL, NULL, 1, '2026-05-04T09:00:00+09:00', '2026-05-04T09:00:00+09:00')
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
  ('9ca330ea-1577-4711-a348-6252f852fc21', '9f7911d7-7c68-4c4d-9d66-8df46e0f36fb', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-05-04T09:00:00+09:00', NULL, '2026-05-04T09:00:00+09:00', '2026-05-04T09:00:00+09:00')
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

COMMIT;

