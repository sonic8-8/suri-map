-- 프론트 전용 mock seed: mock-112 import 흐름을 SQL로 직접 재현한다.
-- precinct-first-scenario.json 기준 (sourceIncidentId: 00000000-0000-0000-0000-000000000001)
--
-- 고정 ID
--   incidentId:         9234ccbb-c52d-3ccd-8d79-ae0d4efac733
--   sourceIncidentId:   00000000-0000-0000-0000-000000000001
--   op1Id:              99999999-9999-9999-9999-999999990001
--   assignmentId (precinct-cmd):  142509d5-9423-3053-8e96-922b17e9cbbd
--   assignmentId (precinct-car):  c39f6cc6-b11d-336f-85d1-4e606543e6bc
--   assignmentId (precinct-team): 93a95358-0eeb-397e-adf9-50e5bbd5a829
--   overallAreaId:      8aa7acce-05d8-4446-9fe4-55b256ed5d8f
--   unitAreaId:         a9f5c92f-0cc0-4638-af8a-c4012e8993ee
--   teamAreaId (north): cdd7c779-6681-43f4-a91d-c9d2f5972edf
--   teamAreaId (south): d396f4e0-d0ce-4d15-82a5-286072d7a4c5

SET client_encoding = 'UTF8';

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS search_area (
  id UUID PRIMARY KEY,
  operational_period_id UUID NOT NULL,
  parent_search_area_id UUID,
  name VARCHAR(120) NOT NULL,
  area_level VARCHAR(24) NOT NULL,
  geometry GEOMETRY(POLYGON, 4326) NOT NULL,
  status VARCHAR(24) NOT NULL,
  version BIGINT NOT NULL,
  created_by_account_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_search_area_area_level CHECK (area_level IN ('OVERALL', 'UNIT', 'TEAM')),
  CONSTRAINT chk_search_area_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT chk_search_area_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_area_one_active_overall_per_op
  ON search_area (operational_period_id)
  WHERE area_level = 'OVERALL' AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_search_area_op_status
  ON search_area (operational_period_id, status);

CREATE INDEX IF NOT EXISTS idx_search_area_parent
  ON search_area (parent_search_area_id);

CREATE INDEX IF NOT EXISTS idx_search_area_geom
  ON search_area
  USING GIST (geometry);

CREATE TABLE IF NOT EXISTS search_area_history (
  id UUID PRIMARY KEY,
  search_area_id UUID NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  previous_status VARCHAR(24),
  next_status VARCHAR(24),
  previous_geometry GEOMETRY(POLYGON, 4326),
  next_geometry GEOMETRY(POLYGON, 4326),
  change_memo TEXT,
  changed_by_account_id UUID NOT NULL,
  changed_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_search_area_history_change_type CHECK (
    change_type IN ('CREATED', 'GEOMETRY_UPDATED', 'STATUS_CHANGED', 'SPLIT')
  )
);

CREATE INDEX IF NOT EXISTS idx_search_area_history_area
  ON search_area_history (search_area_id, changed_at);

-- 오래된 로컬 DB에서 Flyway가 끝까지 돌지 않은 상태로 상황판/구역 화면을 열면
-- board query가 search_area_assignment를 조회하면서 42P01로 실패한다.
-- 이 seed는 프론트 mock 실행용이므로 최신 migration과 같은 최소 shape를 보장한다.
CREATE TABLE IF NOT EXISTS search_area_assignment (
  id UUID PRIMARY KEY,
  search_area_id UUID NOT NULL,
  assigned_account_id UUID NOT NULL,
  assigned_by_account_id UUID NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  status VARCHAR(24) NOT NULL,
  memo TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_search_area_assignment_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_area_assignment_active
  ON search_area_assignment (search_area_id, assigned_account_id)
  WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_search_area_assignment_area
  ON search_area_assignment (search_area_id, created_at);

CREATE INDEX IF NOT EXISTS idx_search_area_assignment_assignee
  ON search_area_assignment (assigned_account_id, status);

BEGIN;

-- ── [1단계] incident INSERT ───────────────────────────────────────────────────
-- IncidentImportService → incidentMapper.insertIncident()

INSERT INTO incident (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at)
VALUES (
  '9234ccbb-c52d-3ccd-8d79-ae0d4efac733',
  '00000000-0000-0000-0000-000000000001',
  '종로구 인왕산 실종 신고',
  'OPEN',
  '2026-04-28T09:00:00+09:00',
  NULL, NULL, 1,
  '2026-04-28T09:00:00+09:00',
  '2026-04-28T09:00:00+09:00'
)
ON CONFLICT (id) DO UPDATE SET
  title      = EXCLUDED.title,
  status     = EXCLUDED.status,
  updated_at = CURRENT_TIMESTAMP;

-- ── [2단계] missing_person INSERT ────────────────────────────────────────────
-- IncidentImportService → insertMissingPerson()

INSERT INTO missing_person (incident_id, display_name, photo_object_key, appearance_text, last_seen_location_text, last_seen_at, imported_at)
VALUES (
  '9234ccbb-c52d-3ccd-8d79-ae0d4efac733',
  '가상 실종자 001',
  'mock-112/missing-person/mock-112-incident-001.jpg',
  '남색 점퍼, 회색 등산화',
  '인왕산 북측 산책로 입구',
  '2026-04-28T08:30:00+09:00',
  '2026-04-28T09:00:00+09:00'
)
ON CONFLICT (incident_id) DO UPDATE SET
  display_name           = EXCLUDED.display_name,
  appearance_text        = EXCLUDED.appearance_text,
  last_seen_location_text = EXCLUDED.last_seen_location_text;

-- ── [3단계] incident_assignment INSERT ───────────────────────────────────────
-- IncidentImportService → insertAssignments() (assignments 필드 3건)
-- assignmentId = UUID.nameUUIDFromBytes("incident-assignment:" + externalAssignmentKey)
-- accountId    = AccountIdentityCatalog.accountIdFromCodeOrUuid(accountCode)

INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at)
VALUES
  -- externalAssignmentKey: 00000000-0000-0000-0000-000000000001:precinct-cmd
  ('142509d5-9423-3053-8e96-922b17e9cbbd', '9234ccbb-c52d-3ccd-8d79-ae0d4efac733',
   '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER',
   '2026-04-28T09:00:00+09:00', NULL,
   '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'),
  -- externalAssignmentKey: 00000000-0000-0000-0000-000000000001:precinct-car
  ('c39f6cc6-b11d-336f-85d1-4e606543e6bc', '9234ccbb-c52d-3ccd-8d79-ae0d4efac733',
   '11111111-1111-1111-1111-111111110007', 'MEMBER',
   '2026-04-28T09:00:00+09:00', NULL,
   '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00'),
  -- externalAssignmentKey: 00000000-0000-0000-0000-000000000001:precinct-team
  ('93a95358-0eeb-397e-adf9-50e5bbd5a829', '9234ccbb-c52d-3ccd-8d79-ae0d4efac733',
   '11111111-1111-1111-1111-111111110004', 'MEMBER',
   '2026-04-28T09:00:00+09:00', NULL,
   '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')
ON CONFLICT (id) DO UPDATE SET
  incident_role = EXCLUDED.incident_role,
  revoked_at    = EXCLUDED.revoked_at,
  updated_at    = CURRENT_TIMESTAMP;

-- ── [4단계] operational_period INSERT (OP1) ───────────────────────────────────
-- IncidentImportService → createOp1() → InitialOperationalPeriodCreationService

INSERT INTO operational_period (id, incident_id, sequence_number, status, reason, reason_memo, started_by_account_id, ended_by_account_id, started_at, ended_at, version, created_at, updated_at)
VALUES (
  '99999999-9999-9999-9999-999999990001',
  '9234ccbb-c52d-3ccd-8d79-ae0d4efac733',
  1, 'ACTIVE', 'INITIAL', NULL, NULL, NULL,
  '2026-04-28T09:00:00+09:00', NULL, 1,
  '2026-04-28T09:00:00+09:00',
  '2026-04-28T09:00:00+09:00'
)
ON CONFLICT (id) DO UPDATE SET
  status     = EXCLUDED.status,
  updated_at = CURRENT_TIMESTAMP;

-- ── [5단계] search_area / search_area_history / search_area_assignment INSERT ──
-- 프론트 구역 편집/상황판 확인용 mock. 사건 import 결과가 아니라 지휘관이 OP1에서 그린 구역 상태를 재현한다.

DELETE FROM search_area_assignment
WHERE search_area_id IN (
  SELECT id
  FROM search_area
  WHERE operational_period_id = '99999999-9999-9999-9999-999999990001'
);

DELETE FROM search_area_history
WHERE search_area_id IN (
  SELECT id
  FROM search_area
  WHERE operational_period_id = '99999999-9999-9999-9999-999999990001'
);

DELETE FROM search_area
WHERE operational_period_id = '99999999-9999-9999-9999-999999990001';

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
  (
    '8aa7acce-05d8-4446-9fe4-55b256ed5d8f',
    '99999999-9999-9999-9999-999999990001',
    NULL,
    '인왕산 전체 수색 구역',
    'OVERALL',
    ST_GeomFromText('POLYGON((126.948000 37.565000,126.968000 37.565000,126.968000 37.579000,126.948000 37.579000,126.948000 37.565000))', 4326),
    'ACTIVE',
    1,
    '11111111-1111-1111-1111-111111110001',
    '2026-04-28T09:05:00+09:00',
    '2026-04-28T09:05:00+09:00'
  ),
  (
    'a9f5c92f-0cc0-4638-af8a-c4012e8993ee',
    '99999999-9999-9999-9999-999999990001',
    '8aa7acce-05d8-4446-9fe4-55b256ed5d8f',
    '인왕산 서측 단위 구역',
    'UNIT',
    ST_GeomFromText('POLYGON((126.952000 37.568000,126.961000 37.568000,126.961000 37.575000,126.952000 37.575000,126.952000 37.568000))', 4326),
    'ACTIVE',
    1,
    '11111111-1111-1111-1111-111111110001',
    '2026-04-28T09:08:00+09:00',
    '2026-04-28T09:08:00+09:00'
  ),
  (
    'cdd7c779-6681-43f4-a91d-c9d2f5972edf',
    '99999999-9999-9999-9999-999999990001',
    'a9f5c92f-0cc0-4638-af8a-c4012e8993ee',
    '인왕산 북측 팀 구역',
    'TEAM',
    ST_GeomFromText('POLYGON((126.952000 37.571500,126.961000 37.571500,126.961000 37.575000,126.952000 37.575000,126.952000 37.571500))', 4326),
    'ACTIVE',
    1,
    '11111111-1111-1111-1111-111111110001',
    '2026-04-28T09:10:00+09:00',
    '2026-04-28T09:10:00+09:00'
  ),
  (
    'd396f4e0-d0ce-4d15-82a5-286072d7a4c5',
    '99999999-9999-9999-9999-999999990001',
    'a9f5c92f-0cc0-4638-af8a-c4012e8993ee',
    '인왕산 남측 팀 구역',
    'TEAM',
    ST_GeomFromText('POLYGON((126.952000 37.568000,126.961000 37.568000,126.961000 37.571500,126.952000 37.571500,126.952000 37.568000))', 4326),
    'ACTIVE',
    1,
    '11111111-1111-1111-1111-111111110001',
    '2026-04-28T09:12:00+09:00',
    '2026-04-28T09:12:00+09:00'
  );

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
SELECT
  history.id,
  area.id,
  'CREATED',
  NULL,
  area.status,
  NULL,
  area.geometry,
  history.memo,
  '11111111-1111-1111-1111-111111110001',
  area.created_at,
  area.created_at
FROM (
  VALUES
    ('44444444-4444-4444-4444-444444440001'::uuid, '8aa7acce-05d8-4446-9fe4-55b256ed5d8f'::uuid, '전체 수색 구역 생성'),
    ('44444444-4444-4444-4444-444444440002'::uuid, 'a9f5c92f-0cc0-4638-af8a-c4012e8993ee'::uuid, '단위 구역 생성'),
    ('44444444-4444-4444-4444-444444440003'::uuid, 'cdd7c779-6681-43f4-a91d-c9d2f5972edf'::uuid, '북측 팀 구역 생성'),
    ('44444444-4444-4444-4444-444444440004'::uuid, 'd396f4e0-d0ce-4d15-82a5-286072d7a4c5'::uuid, '남측 팀 구역 생성')
) AS history(id, search_area_id, memo)
JOIN search_area area ON area.id = history.search_area_id;

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
  (
    '33333333-3333-3333-3333-333333330001',
    'cdd7c779-6681-43f4-a91d-c9d2f5972edf',
    '11111111-1111-1111-1111-111111110004',
    '11111111-1111-1111-1111-111111110001',
    '2026-04-28T09:15:00+09:00',
    NULL,
    'ACTIVE',
    '북측 등산로 입구부터 능선 합류점까지 확인',
    '2026-04-28T09:15:00+09:00',
    '2026-04-28T09:15:00+09:00'
  ),
  (
    '33333333-3333-3333-3333-333333330002',
    'd396f4e0-d0ce-4d15-82a5-286072d7a4c5',
    '11111111-1111-1111-1111-111111110007',
    '11111111-1111-1111-1111-111111110001',
    '2026-04-28T09:16:00+09:00',
    NULL,
    'ACTIVE',
    '남측 산책로와 주차장 연결 지점 확인',
    '2026-04-28T09:16:00+09:00',
    '2026-04-28T09:16:00+09:00'
  );

COMMIT;
