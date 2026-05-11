-- 프론트 전용 mock seed: 사건 가져오기 -> 상황판 -> 구역 분할/담당 배정 테스트.
-- TODO: 실제 112/mock import와 계정 DB 계약이 완성되면 이 스크립트는 제거한다.
--
-- 원칙
-- - 모든 id/account_id/police_phone_id는 PostgreSQL UUID 값으로 고정한다.
-- - 사건 가져오기 직후 상태를 재현하므로 search_area, search_area_assignment는 만들지 않는다.
-- - 이 스크립트는 재실행 가능해야 하므로 같은 mock 사건의 파생 데이터를 먼저 삭제한다.

BEGIN;

-- 고정 mock ID
-- incidentId: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001
-- sourceIncidentId: cccccccc-cccc-cccc-cccc-cccccccc0001
-- op1Id: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101
-- commanderAccountId: 11111111-1111-1111-1111-111111110002
-- teamAccountId: 11111111-1111-1111-1111-111111110004

-- 같은 mock 사건에 붙어 있던 구역/배정/OP 파생 데이터를 제거한다.
DELETE FROM search_area_assignment
WHERE search_area_id IN (
  SELECT sa.id
  FROM search_area sa
  JOIN operational_period op ON op.id = sa.operational_period_id
  WHERE op.incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001'
);

DELETE FROM search_area_history
WHERE search_area_id IN (
  SELECT sa.id
  FROM search_area sa
  JOIN operational_period op ON op.id = sa.operational_period_id
  WHERE op.incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001'
);

DELETE FROM search_area
WHERE operational_period_id IN (
  SELECT id
  FROM operational_period
  WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001'
);

DELETE FROM handover_memo
WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001';

DELETE FROM idempotency_record
WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001';

DELETE FROM operational_period
WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001';

DELETE FROM incident_assignment
WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001';

DELETE FROM missing_person
WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001';

DELETE FROM incident
WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001'
   OR source_incident_id = 'cccccccc-cccc-cccc-cccc-cccccccc0001';

-- 로그인/배정 후보 계정.
INSERT INTO account (
  id,
  login_id,
  password_hash,
  display_name,
  account_type,
  organization_type,
  status
)
VALUES
  (
    '11111111-1111-1111-1111-111111110001',
    '11111111-1111-1111-1111-111111110001',
    '{noop}suri-map-demo',
    '김초동',
    'COMMAND',
    'POLICE_SUBSTATION',
    'ACTIVE'
  ),
  (
    '11111111-1111-1111-1111-111111110002',
    '11111111-1111-1111-1111-111111110002',
    '{noop}suri-map-demo',
    '이수색',
    'COMMAND',
    'MISSING_TEAM',
    'ACTIVE'
  ),
  (
    '11111111-1111-1111-1111-111111110003',
    '11111111-1111-1111-1111-111111110003',
    '{noop}suri-map-demo',
    '박지원',
    'COMMAND',
    'SUPPORT_UNIT',
    'ACTIVE'
  ),
  (
    '11111111-1111-1111-1111-111111110004',
    '11111111-1111-1111-1111-111111110004',
    '{noop}suri-map-demo',
    '정현장',
    'TEAM',
    'MISSING_TEAM',
    'ACTIVE'
  )
ON CONFLICT (id) DO UPDATE
SET
  login_id = EXCLUDED.login_id,
  password_hash = EXCLUDED.password_hash,
  display_name = EXCLUDED.display_name,
  account_type = EXCLUDED.account_type,
  organization_type = EXCLUDED.organization_type,
  status = EXCLUDED.status,
  updated_at = CURRENT_TIMESTAMP;

-- 지휘 계정 police_phone은 Android FCM 대상이 아니라 웹 지휘 fixture 식별자 용도다.
INSERT INTO police_phone (
  id,
  phone_code,
  display_name,
  account_id,
  status,
  last_heartbeat_at,
  last_sync_at
)
VALUES
  (
    '22222222-2222-2222-2222-222222220002',
    'mock-alpha-command-phone',
    '실종팀 지휘 폴리폰',
    '11111111-1111-1111-1111-111111110002',
    'ACTIVE',
    NULL,
    NULL
  ),
  (
    '22222222-2222-2222-2222-222222220004',
    'mock-alpha-team-phone',
    '실종팀 현장 폴리폰',
    '11111111-1111-1111-1111-111111110004',
    'ACTIVE',
    NULL,
    NULL
  )
ON CONFLICT (id) DO UPDATE
SET
  phone_code = EXCLUDED.phone_code,
  display_name = EXCLUDED.display_name,
  account_id = EXCLUDED.account_id,
  status = EXCLUDED.status,
  last_heartbeat_at = EXCLUDED.last_heartbeat_at,
  last_sync_at = EXCLUDED.last_sync_at,
  updated_at = CURRENT_TIMESTAMP;

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
VALUES (
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
  'cccccccc-cccc-cccc-cccc-cccccccc0001',
  '종로 실종 상황판 테스트 사건',
  'OPEN',
  '2026-04-28T09:00:00+09:00',
  NULL,
  NULL,
  1,
  '2026-04-28T09:00:00+09:00',
  '2026-04-28T09:00:00+09:00'
);

INSERT INTO missing_person (
  incident_id,
  display_name,
  photo_object_key,
  appearance_text,
  last_seen_location_text,
  last_seen_at,
  imported_at
)
VALUES (
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
  '홍길동',
  'mock-112/missing-person/cccccccc-cccc-cccc-cccc-cccccccc0001.jpg',
  '회색 후드, 검정색 배낭',
  '종로 커뮤니티센터 인근',
  '2026-04-28T08:30:00+09:00',
  '2026-04-28T09:00:00+09:00'
);

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
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0201',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    '11111111-1111-1111-1111-111111110002',
    'INCIDENT_COMMANDER',
    '2026-04-28T09:00:00+09:00',
    NULL,
    '2026-04-28T09:00:00+09:00',
    '2026-04-28T09:00:00+09:00'
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0202',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    '11111111-1111-1111-1111-111111110004',
    'MEMBER',
    '2026-04-28T09:00:00+09:00',
    NULL,
    '2026-04-28T09:00:00+09:00',
    '2026-04-28T09:00:00+09:00'
  );

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
VALUES (
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
  1,
  'ACTIVE',
  'INITIAL',
  NULL,
  '11111111-1111-1111-1111-111111110002',
  NULL,
  '2026-04-28T09:00:00+09:00',
  NULL,
  1,
  '2026-04-28T09:00:00+09:00',
  '2026-04-28T09:00:00+09:00'
);

COMMIT;
