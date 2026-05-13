-- 종류별 마커 mock 데이터
-- 기준 DB 상태:
-- - incident_id: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001
-- - op_id: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101
-- - created_by_account_id: 11111111-1111-1111-1111-111111110004
-- - police_phone_id: 22222222-2222-2222-2222-222222220004

BEGIN;

DELETE FROM marker
WHERE id IN (
    '72000000-0000-0000-0000-000000000001',
    '72000000-0000-0000-0000-000000000002',
    '72000000-0000-0000-0000-000000000003',
    '72000000-0000-0000-0000-000000000004',
    '72000000-0000-0000-0000-000000000005',
    '72000000-0000-0000-0000-000000000006',
    '72000000-0000-0000-0000-000000000007'
);

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
) VALUES
(
    '72000000-0000-0000-0000-000000000001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
    NULL,
    'CLUE',
    NULL,
    ST_SetSRID(ST_MakePoint(126.808500, 35.164000), 4326),
    'TEAM 1 구역 내 신발 자국 추정 단서',
    '2026-05-11T00:12:00+09:00',
    '11111111-1111-1111-1111-111111110004',
    '22222222-2222-2222-2222-222222220004',
    'MOCK_SEED',
    'ACTIVE',
    1,
    '2026-05-11T00:12:00+09:00',
    '2026-05-11T00:12:00+09:00'
),
(
    '72000000-0000-0000-0000-000000000002',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
    NULL,
    'PERSON_FOUND',
    NULL,
    ST_SetSRID(ST_MakePoint(126.844000, 35.181000), 4326),
    '발견 신고 지점 확인 필요',
    '2026-05-11T00:28:00+09:00',
    '11111111-1111-1111-1111-111111110004',
    '22222222-2222-2222-2222-222222220004',
    'MOCK_SEED',
    'ACTIVE',
    1,
    '2026-05-11T00:28:00+09:00',
    '2026-05-11T00:28:00+09:00'
),
(
    '72000000-0000-0000-0000-000000000003',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
    NULL,
    'FIELD_CONDITION',
    NULL,
    ST_SetSRID(ST_MakePoint(126.816000, 35.184000), 4326),
    '경사 심함, 도보 수색 속도 저하',
    '2026-05-11T00:41:00+09:00',
    '11111111-1111-1111-1111-111111110004',
    '22222222-2222-2222-2222-222222220004',
    'MOCK_SEED',
    'ACTIVE',
    1,
    '2026-05-11T00:41:00+09:00',
    '2026-05-11T00:41:00+09:00'
),
(
    '72000000-0000-0000-0000-000000000004',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
    NULL,
    'NOTE',
    NULL,
    ST_SetSRID(ST_MakePoint(126.825000, 35.174000), 4326),
    'UNIT 경계 인근 교차 확인 지점',
    '2026-05-11T00:55:00+09:00',
    '11111111-1111-1111-1111-111111110004',
    '22222222-2222-2222-2222-222222220004',
    'MOCK_SEED',
    'ACTIVE',
    1,
    '2026-05-11T00:55:00+09:00',
    '2026-05-11T00:55:00+09:00'
),
(
    '72000000-0000-0000-0000-000000000005',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
    NULL,
    'SUPPORT_REQUEST',
    'DRONE',
    ST_SetSRID(ST_MakePoint(126.835000, 35.166000), 4326),
    '수목 밀집 구간 드론 확인 요청',
    '2026-05-11T01:08:00+09:00',
    '11111111-1111-1111-1111-111111110004',
    '22222222-2222-2222-2222-222222220004',
    'MOCK_SEED',
    'ACTIVE',
    1,
    '2026-05-11T01:08:00+09:00',
    '2026-05-11T01:08:00+09:00'
),
(
    '72000000-0000-0000-0000-000000000006',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
    NULL,
    'SUPPORT_REQUEST',
    'POLICE_DOG',
    ST_SetSRID(ST_MakePoint(126.804500, 35.186000), 4326),
    '흔적 추적을 위한 경찰견 지원 요청',
    '2026-05-11T01:22:00+09:00',
    '11111111-1111-1111-1111-111111110004',
    '22222222-2222-2222-2222-222222220004',
    'MOCK_SEED',
    'ACTIVE',
    1,
    '2026-05-11T01:22:00+09:00',
    '2026-05-11T01:22:00+09:00'
),
(
    '72000000-0000-0000-0000-000000000007',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0101',
    NULL,
    'SUPPORT_REQUEST',
    'OTHER',
    ST_SetSRID(ST_MakePoint(126.849000, 35.161500), 4326),
    '차량 진입 통제 및 추가 인력 지원 요청',
    '2026-05-11T01:36:00+09:00',
    '11111111-1111-1111-1111-111111110004',
    '22222222-2222-2222-2222-222222220004',
    'MOCK_SEED',
    'ACTIVE',
    1,
    '2026-05-11T01:36:00+09:00',
    '2026-05-11T01:36:00+09:00'
);

COMMIT;
