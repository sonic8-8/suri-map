-- 프론트 오프라인 패키지 상태 화면 mock seed.
-- 전제: 같은 디렉터리의 test.sql을 먼저 실행해 mock 사건/OP1/기본 계정을 만든다.
--
-- 문서 정합성 원칙
-- - 오프라인 패키지는 active 전체 수색 구역(OVERALL)이 있을 때만 READY 상태가 될 수 있다.
-- - 패키지 상태는 폴리폰별 설치 보고 결과이며, 수색 누락/다음 구역 추천/위험도 판단을 표현하지 않는다.
-- - PURGED 상태는 사건 종료 후 tombstone 성격의 상태만 남기고 상세 항목을 제거한다.

BEGIN;

-- 기준 mock 사건
-- incidentId: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001
-- op1Id: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101
-- missingTeamCommanderAccountId: 11111111-1111-1111-1111-111111110002

-- 재실행 가능하도록 이 mock 사건의 오프라인 패키지 row만 제거한다.
DELETE FROM offline_package_installation
WHERE offline_package_manifest_id IN (
  SELECT id
  FROM offline_package_manifest
  WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001'
);

DELETE FROM offline_package_manifest
WHERE incident_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001';

-- 패키지 상태를 볼 폴리폰 계정 fixture. 팀/순찰차 폴리폰은 APP 패키지 설치 보고 주체다.
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
    '11111111-1111-1111-1111-111111110005',
    '11111111-1111-1111-1111-111111110005',
    '{noop}suri-map-demo',
    '강북팀',
    'TEAM',
    'SUPPORT_UNIT',
    'ACTIVE'
  ),
  (
    '11111111-1111-1111-1111-111111110006',
    '11111111-1111-1111-1111-111111110006',
    '{noop}suri-map-demo',
    '남측팀',
    'TEAM',
    'SUPPORT_UNIT',
    'ACTIVE'
  ),
  (
    '11111111-1111-1111-1111-111111110007',
    '11111111-1111-1111-1111-111111110007',
    '{noop}suri-map-demo',
    '종로순찰1',
    'PATROL_CAR',
    'POLICE_SUBSTATION',
    'ACTIVE'
  ),
  (
    '11111111-1111-1111-1111-111111110008',
    '11111111-1111-1111-1111-111111110008',
    '{noop}suri-map-demo',
    '종로순찰2',
    'PATROL_CAR',
    'POLICE_SUBSTATION',
    'ACTIVE'
  ),
  (
    '11111111-1111-1111-1111-111111110009',
    '11111111-1111-1111-1111-111111110009',
    '{noop}suri-map-demo',
    '파기확인',
    'TEAM',
    'SUPPORT_UNIT',
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
    '22222222-2222-2222-2222-222222220005',
    'mock-alpha-support-team-01',
    '기동대 1부대 팀 폴리폰',
    '11111111-1111-1111-1111-111111110005',
    'ACTIVE',
    '2026-04-28T09:17:00+09:00',
    '2026-04-28T09:16:30+09:00'
  ),
  (
    '22222222-2222-2222-2222-222222220006',
    'mock-alpha-support-team-02',
    '기동대 2부대 팀 폴리폰',
    '11111111-1111-1111-1111-111111110006',
    'ACTIVE',
    '2026-04-28T09:14:00+09:00',
    '2026-04-28T09:09:30+09:00'
  ),
  (
    '22222222-2222-2222-2222-222222220007',
    'mock-alpha-local-patrol-01',
    '종로 지구대 순찰차 폴리폰 1',
    '11111111-1111-1111-1111-111111110007',
    'ACTIVE',
    '2026-04-28T09:05:00+09:00',
    '2026-04-28T09:01:00+09:00'
  ),
  (
    '22222222-2222-2222-2222-222222220008',
    'mock-alpha-local-patrol-02',
    '종로 지구대 순찰차 폴리폰 2',
    '11111111-1111-1111-1111-111111110008',
    'ACTIVE',
    NULL,
    NULL
  ),
  (
    '22222222-2222-2222-2222-222222220009',
    'mock-alpha-purged-phone',
    '파기 확인용 팀 폴리폰',
    '11111111-1111-1111-1111-111111110009',
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

DELETE FROM incident_assignment
WHERE id IN (
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0205',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0206',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0207',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0208',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0209'
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
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0205',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    '11111111-1111-1111-1111-111111110005',
    'MEMBER',
    '2026-04-28T09:03:00+09:00',
    NULL,
    '2026-04-28T09:03:00+09:00',
    '2026-04-28T09:03:00+09:00'
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0206',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    '11111111-1111-1111-1111-111111110006',
    'MEMBER',
    '2026-04-28T09:03:00+09:00',
    NULL,
    '2026-04-28T09:03:00+09:00',
    '2026-04-28T09:03:00+09:00'
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0207',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    '11111111-1111-1111-1111-111111110007',
    'MEMBER',
    '2026-04-28T09:03:00+09:00',
    NULL,
    '2026-04-28T09:03:00+09:00',
    '2026-04-28T09:03:00+09:00'
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0208',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    '11111111-1111-1111-1111-111111110008',
    'MEMBER',
    '2026-04-28T09:03:00+09:00',
    NULL,
    '2026-04-28T09:03:00+09:00',
    '2026-04-28T09:03:00+09:00'
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0209',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    '11111111-1111-1111-1111-111111110009',
    'MEMBER',
    '2026-04-28T09:03:00+09:00',
    NULL,
    '2026-04-28T09:03:00+09:00',
    '2026-04-28T09:03:00+09:00'
  );

-- READY 패키지가 문서와 충돌하지 않도록 active 전체 수색 구역을 보장한다.
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
SELECT
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0301',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
  NULL,
  '종로 전체 수색 구역',
  'OVERALL',
  ST_GeomFromText(
    'POLYGON((126.9720 37.5680,126.9865 37.5680,126.9865 37.5795,126.9720 37.5795,126.9720 37.5680))',
    4326
  ),
  'ACTIVE',
  1,
  '11111111-1111-1111-1111-111111110002',
  '2026-04-28T09:04:00+09:00',
  '2026-04-28T09:04:00+09:00'
WHERE NOT EXISTS (
  SELECT 1
  FROM search_area
  WHERE operational_period_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101'
    AND area_level = 'OVERALL'
    AND status = 'ACTIVE'
);

-- v1은 이전 manifest, v2는 최신 active manifest다.
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
SELECT
  'pkg-alpha-manifest-v1',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
  1,
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
  active_overall.id::text,
  active_overall.version,
  'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  1,
  jsonb_build_object(
    'incidentId', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'manifestVersion', 1,
    'overallSearchAreaId', active_overall.id::text,
    'packageItems', jsonb_build_array('incident', 'missing_person', 'operational_periods', 'overall_search_area', 'tileItems')
  ),
  '2026-04-29T09:04:00+09:00',
  '2026-04-28T09:04:00+09:00',
  '2026-04-28T09:04:00+09:00'
FROM (
  SELECT id, version
  FROM search_area
  WHERE operational_period_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101'
    AND area_level = 'OVERALL'
    AND status = 'ACTIVE'
  ORDER BY updated_at DESC
  LIMIT 1
) active_overall;

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
SELECT
  'pkg-alpha-manifest-v2',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
  2,
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
  active_overall.id::text,
  active_overall.version,
  'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
  1,
  jsonb_build_object(
    'incidentId', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'manifestVersion', 2,
    'overallSearchAreaId', active_overall.id::text,
    'packageItems', jsonb_build_array('incident', 'missing_person', 'operational_periods', 'assigned_areas', 'initial_markers', 'overall_search_area', 'tileItems')
  ),
  '2026-04-29T09:20:00+09:00',
  '2026-04-28T09:20:00+09:00',
  '2026-04-28T09:20:00+09:00'
FROM (
  SELECT id, version
  FROM search_area
  WHERE operational_period_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101'
    AND area_level = 'OVERALL'
    AND status = 'ACTIVE'
  ORDER BY updated_at DESC
  LIMIT 1
) active_overall;

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
  (
    'pkg-alpha-install-ready-team',
    'pkg-alpha-manifest-v2',
    '22222222-2222-2222-2222-222222220004',
    '11111111-1111-1111-1111-111111110004',
    'READY',
    7,
    7,
    0,
    NULL,
    NULL,
    '2026-04-28T09:24:00+09:00',
    3,
    '2026-04-28T09:20:30+09:00',
    '2026-04-28T09:24:00+09:00'
  ),
  (
    'pkg-alpha-install-ready-support',
    'pkg-alpha-manifest-v2',
    '22222222-2222-2222-2222-222222220005',
    '11111111-1111-1111-1111-111111110005',
    'READY',
    7,
    7,
    0,
    NULL,
    NULL,
    '2026-04-28T09:25:00+09:00',
    2,
    '2026-04-28T09:21:00+09:00',
    '2026-04-28T09:25:00+09:00'
  ),
  (
    'pkg-alpha-install-partial-support',
    'pkg-alpha-manifest-v2',
    '22222222-2222-2222-2222-222222220006',
    '11111111-1111-1111-1111-111111110006',
    'PARTIAL',
    7,
    5,
    2,
    ARRAY['tile:gwangsan:14/13941/6387', 'initial_markers'],
    'tile_unavailable',
    '2026-04-28T09:24:30+09:00',
    4,
    '2026-04-28T09:21:30+09:00',
    '2026-04-28T09:24:30+09:00'
  ),
  (
    'pkg-alpha-install-stale-patrol',
    'pkg-alpha-manifest-v1',
    '22222222-2222-2222-2222-222222220007',
    '11111111-1111-1111-1111-111111110007',
    'STALE',
    5,
    5,
    0,
    NULL,
    NULL,
    '2026-04-28T09:10:00+09:00',
    5,
    '2026-04-28T09:05:00+09:00',
    '2026-04-28T09:22:00+09:00'
  ),
  (
    'pkg-alpha-install-failed-patrol',
    'pkg-alpha-manifest-v2',
    '22222222-2222-2222-2222-222222220008',
    '11111111-1111-1111-1111-111111110008',
    'FAILED',
    7,
    3,
    4,
    ARRAY['missing_person', 'assigned_areas', 'tile:gwangsan:15/27882/12775', 'tile:gwangsan:15/27882/12776'],
    'package_download_failed',
    '2026-04-28T09:23:00+09:00',
    2,
    '2026-04-28T09:21:00+09:00',
    '2026-04-28T09:23:00+09:00'
  ),
  (
    'pkg-alpha-install-purged-demo',
    'pkg-alpha-manifest-v1',
    '22222222-2222-2222-2222-222222220009',
    NULL,
    'PURGED',
    0,
    0,
    0,
    NULL,
    NULL,
    NULL,
    9,
    '2026-04-28T09:04:00+09:00',
    '2026-04-28T10:30:00+09:00'
  );

COMMIT;
