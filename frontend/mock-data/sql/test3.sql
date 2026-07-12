-- Suri-Map frontend mock data set #3.
-- Large incident fixture for UI stress checks:
-- - 20 active incident assignments
-- - fake accounts and PolicePhones
-- - OP history, areas, duty shifts, paths, markers, photos, notifications, handover memos, summaries, package status
--
-- Main incident id: e3000000-0000-4000-8000-000000000001

SET client_encoding = 'UTF8';
CREATE EXTENSION IF NOT EXISTS postgis;

BEGIN;

-- Remove previous test3 rows in dependency order.
DELETE FROM offline_package_installation
WHERE offline_package_manifest_id IN (
    SELECT id FROM offline_package_manifest WHERE incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM offline_package_manifest
WHERE incident_id = 'e3000000-0000-4000-8000-000000000001';

DELETE FROM search_history_summary
WHERE operational_period_id IN (
    SELECT id FROM operational_period WHERE incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM handover_memo
WHERE operational_period_id IN (
    SELECT id FROM operational_period WHERE incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM marker_notification
WHERE marker_id IN (
    SELECT id FROM marker WHERE incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM photo
WHERE marker_id IN (
    SELECT id FROM marker WHERE incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM marker
WHERE incident_id = 'e3000000-0000-4000-8000-000000000001';

DELETE FROM search_path_lifecycle_event
WHERE search_path_id IN (
    SELECT sp.id
    FROM search_path sp
    JOIN duty_shift ds ON ds.id = sp.duty_shift_id
    JOIN operational_period op ON op.id = ds.operational_period_id
    WHERE op.incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM search_path_excluded_point
WHERE search_path_id IN (
    SELECT sp.id
    FROM search_path sp
    JOIN duty_shift ds ON ds.id = sp.duty_shift_id
    JOIN operational_period op ON op.id = ds.operational_period_id
    WHERE op.incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM search_path_segment
WHERE search_path_id IN (
    SELECT sp.id
    FROM search_path sp
    JOIN duty_shift ds ON ds.id = sp.duty_shift_id
    JOIN operational_period op ON op.id = ds.operational_period_id
    WHERE op.incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM search_path
WHERE duty_shift_id IN (
    SELECT ds.id
    FROM duty_shift ds
    JOIN operational_period op ON op.id = ds.operational_period_id
    WHERE op.incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM duty_shift
WHERE operational_period_id IN (
    SELECT id FROM operational_period WHERE incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM search_area_assignment
WHERE search_area_id IN (
    SELECT sa.id
    FROM search_area sa
    JOIN operational_period op ON op.id = sa.operational_period_id
    WHERE op.incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM search_area_history
WHERE search_area_id IN (
    SELECT sa.id
    FROM search_area sa
    JOIN operational_period op ON op.id = sa.operational_period_id
    WHERE op.incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM search_area
WHERE operational_period_id IN (
    SELECT id FROM operational_period WHERE incident_id = 'e3000000-0000-4000-8000-000000000001'
);

DELETE FROM operational_period
WHERE incident_id = 'e3000000-0000-4000-8000-000000000001';

DELETE FROM incident_assignment
WHERE incident_id = 'e3000000-0000-4000-8000-000000000001';

DELETE FROM missing_person
WHERE incident_id = 'e3000000-0000-4000-8000-000000000001';

DELETE FROM incident
WHERE id = 'e3000000-0000-4000-8000-000000000001';

DELETE FROM fcm_token
WHERE account_id IN (
    SELECT id FROM account WHERE login_id LIKE 'test3-%'
)
   OR police_phone_id IN (
    SELECT id FROM police_phone WHERE phone_code LIKE 'test3-%'
);

DELETE FROM police_phone
WHERE phone_code LIKE 'test3-%';

DELETE FROM account
WHERE login_id LIKE 'test3-%';

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
SELECT
    ('e1000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    'test3-account-' || lpad(gs::text, 2, '0'),
    '{noop}fixture',
    CASE gs
        WHEN 1 THEN '광주경찰청 실종팀 지휘관 이서준'
        WHEN 2 THEN '광주경찰청 실종팀 현장팀 강하늘'
        WHEN 3 THEN '광주경찰청 실종팀 현장팀 박지우'
        WHEN 4 THEN '광주북부경찰서 산수지구대 순찰차 1호'
        WHEN 5 THEN '광주북부경찰서 산수지구대 순찰팀 김민재'
        WHEN 6 THEN '광주동부경찰서 지산파출소 현장팀 오세린'
        WHEN 7 THEN '광주경찰청 기동대 지휘관 한도윤'
        WHEN 8 THEN '광주경찰청 기동대 1제대'
        WHEN 9 THEN '광주경찰청 기동대 2제대'
        WHEN 10 THEN '광주경찰청 드론수색팀'
        WHEN 11 THEN '광주경찰청 경찰견지원팀'
        WHEN 12 THEN '광주북부소방서 산악구조대'
        WHEN 13 THEN '광주도시철도 수색지원반'
        WHEN 14 THEN '무등산국립공원 순찰지원반'
        WHEN 15 THEN '광주자치경찰 교통통제반'
        WHEN 16 THEN '광주경찰청 야간예비조'
        WHEN 17 THEN '광주북부경찰서 탐문지원반'
        WHEN 18 THEN '광주동부경찰서 CCTV확인반'
        WHEN 19 THEN '광주경찰청 통신지원반'
        ELSE '광주경찰청 현장기록반'
    END,
    CASE
        WHEN gs IN (1, 7) THEN 'COMMAND'
        WHEN gs IN (4, 15) THEN 'PATROL_CAR'
        ELSE 'TEAM'
    END,
    CASE
        WHEN gs BETWEEN 1 AND 3 THEN 'MISSING_TEAM'
        WHEN gs IN (4, 5, 6, 15, 17, 18) THEN 'POLICE_SUBSTATION'
        ELSE 'SUPPORT_UNIT'
    END,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 20) AS gs
ON CONFLICT (id) DO UPDATE SET
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
    registered,
    last_heartbeat_at,
    last_sync_at,
    version,
    heartbeat_sequence,
    last_heartbeat_event_id,
    created_at,
    updated_at
)
SELECT
    ('e2000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    'test3-phone-' || lpad(gs::text, 2, '0'),
    'test3 현장 단말 ' || lpad(gs::text, 2, '0'),
    ('e1000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    'ACTIVE',
    TRUE,
    CASE WHEN gs IN (16, 19) THEN NULL ELSE CURRENT_TIMESTAMP - (gs || ' minutes')::interval END,
    CASE WHEN gs IN (16, 19) THEN NULL ELSE CURRENT_TIMESTAMP - ((gs + 1) || ' minutes')::interval END,
    gs + 1,
    2000 + gs,
    CASE WHEN gs IN (16, 19) THEN NULL ELSE ('e2990000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid END,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 20) AS gs
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
    'e3000000-0000-4000-8000-000000000001',
    'e3000000-0000-4000-8000-000000000099',
    '무등산 원효계곡 대규모 실종 신고',
    'OPEN',
    '2026-05-19T08:10:00+09:00',
    NULL,
    NULL,
    9,
    '2026-05-19T08:10:00+09:00',
    '2026-05-19T13:15:00+09:00'
)
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
VALUES (
    'e3000000-0000-4000-8000-000000000001',
    '김가온',
    'mock-112/missing-person/test3-mudeungsan-gaon.jpg',
    '남색 등산 재킷, 회색 등산모, 검은 배낭, 흰색 등산화 착용. 휴대전화 배터리 부족 신고 후 연락 두절.',
    '무등산 원효사 입구에서 꼬막재 방향 등산로 진입 지점',
    '2026-05-19T07:35:00+09:00',
    '2026-05-19T08:12:00+09:00'
)
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
SELECT
    ('e3100000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    'e3000000-0000-4000-8000-000000000001',
    ('e1000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    CASE
        WHEN gs = 1 THEN 'INCIDENT_COMMANDER'
        WHEN gs IN (4, 7, 12, 14) THEN 'FIELD_COMMANDER'
        ELSE 'MEMBER'
    END,
    '2026-05-19T08:15:00+09:00'::timestamptz + (gs || ' minutes')::interval,
    NULL,
    '2026-05-19T08:15:00+09:00'::timestamptz + (gs || ' minutes')::interval,
    '2026-05-19T08:15:00+09:00'::timestamptz + (gs || ' minutes')::interval
FROM generate_series(1, 20) AS gs
ON CONFLICT (id) DO UPDATE SET
    account_id = EXCLUDED.account_id,
    incident_role = EXCLUDED.incident_role,
    revoked_at = EXCLUDED.revoked_at,
    updated_at = CURRENT_TIMESTAMP;

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
    ('e3200000-0000-4000-8000-000000000001', 'e3000000-0000-4000-8000-000000000001', 1, 'ENDED', 'INITIAL', '원효사 입구, 주차장, 상가 주변 초동 탐문과 CCTV 확인', 'e1000000-0000-4000-8000-000000000004', 'e1000000-0000-4000-8000-000000000001', '2026-05-19T08:20:00+09:00', '2026-05-19T10:05:00+09:00', 3, '2026-05-19T08:20:00+09:00', '2026-05-19T10:05:00+09:00'),
    ('e3200000-0000-4000-8000-000000000002', 'e3000000-0000-4000-8000-000000000001', 2, 'ENDED', 'RE_SEARCH', '탐문 결과를 반영해 꼬막재와 늦재 사이 계곡부 재수색', 'e1000000-0000-4000-8000-000000000001', 'e1000000-0000-4000-8000-000000000001', '2026-05-19T10:10:00+09:00', '2026-05-19T12:25:00+09:00', 4, '2026-05-19T10:10:00+09:00', '2026-05-19T12:25:00+09:00'),
    ('e3200000-0000-4000-8000-000000000003', 'e3000000-0000-4000-8000-000000000001', 3, 'ACTIVE', 'RE_SEARCH', '북동 능선과 약수터 주변을 넓혀 드론, 경찰견, 도보조 합동 수색', 'e1000000-0000-4000-8000-000000000001', NULL, '2026-05-19T12:35:00+09:00', NULL, 5, '2026-05-19T12:35:00+09:00', '2026-05-19T13:15:00+09:00')
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    reason = EXCLUDED.reason,
    reason_memo = EXCLUDED.reason_memo,
    ended_by_account_id = EXCLUDED.ended_by_account_id,
    ended_at = EXCLUDED.ended_at,
    version = EXCLUDED.version,
    updated_at = EXCLUDED.updated_at;

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
    ('e3300000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000001', NULL, 'OP1 원효사 초동 전체 구역', 'OVERALL', ST_SetSRID(ST_GeomFromText('POLYGON((126.9900 35.1420,127.0060 35.1420,127.0060 35.1545,126.9900 35.1545,126.9900 35.1420))'), 4326), 'COMPLETED', 2, 'e1000000-0000-4000-8000-000000000004', '2026-05-19T08:22:00+09:00', '2026-05-19T10:05:00+09:00'),
    ('e3300000-0000-4000-8000-000000000002', 'e3200000-0000-4000-8000-000000000002', NULL, 'OP2 꼬막재 계곡 전체 구역', 'OVERALL', ST_SetSRID(ST_GeomFromText('POLYGON((126.9820 35.1430,127.0160 35.1430,127.0180 35.1650,126.9880 35.1680,126.9800 35.1560,126.9820 35.1430))'), 4326), 'COMPLETED', 3, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T10:12:00+09:00', '2026-05-19T12:25:00+09:00'),
    ('e3300000-0000-4000-8000-000000000003', 'e3200000-0000-4000-8000-000000000003', NULL, 'OP3 북동 능선 확대 전체 구역', 'OVERALL', ST_SetSRID(ST_GeomFromText('POLYGON((126.9780 35.1410,127.0250 35.1410,127.0260 35.1750,126.9900 35.1780,126.9760 35.1600,126.9780 35.1410))'), 4326), 'ACTIVE', 5, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:35:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000011', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000003', 'A구역 원효사 입구·주차장', 'UNIT', ST_SetSRID(ST_GeomFromText('POLYGON((126.9880 35.1430,126.9990 35.1430,126.9990 35.1520,126.9870 35.1530,126.9880 35.1430))'), 4326), 'COMPLETED', 3, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:37:00+09:00', '2026-05-19T13:00:00+09:00'),
    ('e3300000-0000-4000-8000-000000000012', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000003', 'B구역 꼬막재 등산로·계곡부', 'UNIT', ST_SetSRID(ST_GeomFromText('POLYGON((126.9990 35.1440,127.0140 35.1450,127.0170 35.1580,127.0060 35.1640,126.9980 35.1550,126.9990 35.1440))'), 4326), 'ACTIVE', 4, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:37:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000013', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000003', 'C구역 늦재 방향 능선', 'UNIT', ST_SetSRID(ST_GeomFromText('POLYGON((126.9900 35.1580,127.0060 35.1640,127.0000 35.1760,126.9840 35.1700,126.9900 35.1580))'), 4326), 'ACTIVE', 3, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:38:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000014', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000003', 'D구역 약수터·임도 접점', 'UNIT', ST_SetSRID(ST_GeomFromText('POLYGON((127.0060 35.1580,127.0230 35.1570,127.0240 35.1720,127.0040 35.1750,127.0060 35.1580))'), 4326), 'ACTIVE', 3, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:38:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000021', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000011', 'A-1 주차장·상가 뒤편', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((126.9890 35.1440,126.9940 35.1440,126.9940 35.1500,126.9885 35.1505,126.9890 35.1440))'), 4326), 'COMPLETED', 2, 'e1000000-0000-4000-8000-000000000004', '2026-05-19T12:40:00+09:00', '2026-05-19T13:00:00+09:00'),
    ('e3300000-0000-4000-8000-000000000022', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000011', 'A-2 원효사 진입로 숲 경계', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((126.9940 35.1440,126.9990 35.1445,126.9980 35.1520,126.9935 35.1510,126.9940 35.1440))'), 4326), 'ACTIVE', 2, 'e1000000-0000-4000-8000-000000000004', '2026-05-19T12:40:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000023', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000012', 'B-1 계곡 하부 돌길', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((127.0000 35.1460,127.0070 35.1465,127.0070 35.1540,126.9990 35.1530,127.0000 35.1460))'), 4326), 'ACTIVE', 3, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:41:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000024', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000012', 'B-2 계곡 상부 합류점', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((127.0070 35.1480,127.0150 35.1490,127.0160 35.1580,127.0060 35.1620,127.0070 35.1480))'), 4326), 'ACTIVE', 3, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:41:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000025', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000013', 'C-1 늦재 남측 능선', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((126.9910 35.1600,127.0000 35.1640,126.9970 35.1720,126.9860 35.1680,126.9910 35.1600))'), 4326), 'ACTIVE', 2, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:42:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000026', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000013', 'C-2 늦재 북측 능선', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((126.9970 35.1640,127.0040 35.1660,127.0010 35.1760,126.9940 35.1730,126.9970 35.1640))'), 4326), 'ACTIVE', 2, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:42:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000027', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000014', 'D-1 약수터 주변', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((127.0080 35.1590,127.0160 35.1585,127.0170 35.1680,127.0060 35.1700,127.0080 35.1590))'), 4326), 'ACTIVE', 2, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:43:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3300000-0000-4000-8000-000000000028', 'e3200000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000014', 'D-2 임도 교차로·통신 음영', 'TEAM', ST_SetSRID(ST_GeomFromText('POLYGON((127.0160 35.1585,127.0240 35.1590,127.0230 35.1720,127.0165 35.1690,127.0160 35.1585))'), 4326), 'ACTIVE', 2, 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:43:00+09:00', '2026-05-19T13:15:00+09:00')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    area_level = EXCLUDED.area_level,
    geometry = EXCLUDED.geometry,
    status = EXCLUDED.status,
    version = EXCLUDED.version,
    updated_at = EXCLUDED.updated_at;

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
    ('e3310000-0000-4000-8000-000000000001', 'e3300000-0000-4000-8000-000000000003', 'CREATED', NULL, 'ACTIVE', NULL, ST_SetSRID(ST_GeomFromText('POLYGON((126.9780 35.1410,127.0250 35.1410,127.0260 35.1750,126.9900 35.1780,126.9760 35.1600,126.9780 35.1410))'), 4326), 'OP3 확대 수색 전체 구역 생성', 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:35:00+09:00', '2026-05-19T12:35:00+09:00'),
    ('e3310000-0000-4000-8000-000000000002', 'e3300000-0000-4000-8000-000000000021', 'STATUS_CHANGED', 'ACTIVE', 'COMPLETED', NULL, NULL, '주차장과 상가 뒤편 탐문 완료', 'e1000000-0000-4000-8000-000000000004', '2026-05-19T13:00:00+09:00', '2026-05-19T13:00:00+09:00'),
    ('e3310000-0000-4000-8000-000000000003', 'e3300000-0000-4000-8000-000000000028', 'CREATED', NULL, 'ACTIVE', NULL, ST_SetSRID(ST_GeomFromText('POLYGON((127.0160 35.1585,127.0240 35.1590,127.0230 35.1720,127.0165 35.1690,127.0160 35.1585))'), 4326), '통신 음영 지역 별도 팀 구역 생성', 'e1000000-0000-4000-8000-000000000019', '2026-05-19T12:43:00+09:00', '2026-05-19T12:43:00+09:00')
ON CONFLICT (id) DO NOTHING;

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
SELECT
    ('e3320000-0000-4000-8000-' || lpad((row_number() OVER ())::text, 12, '0'))::uuid,
    area_id,
    account_id,
    assigned_by,
    assigned_at,
    NULL,
    'ACTIVE',
    memo,
    assigned_at,
    assigned_at
FROM (
    VALUES
        ('e3300000-0000-4000-8000-000000000021'::uuid, 'e1000000-0000-4000-8000-000000000005'::uuid, 'e1000000-0000-4000-8000-000000000004'::uuid, 'A-1 주차장 탐문 완료 확인', '2026-05-19T12:45:00+09:00'::timestamptz),
        ('e3300000-0000-4000-8000-000000000022'::uuid, 'e1000000-0000-4000-8000-000000000006'::uuid, 'e1000000-0000-4000-8000-000000000004'::uuid, 'A-2 숲 경계 도보 확인', '2026-05-19T12:45:00+09:00'::timestamptz),
        ('e3300000-0000-4000-8000-000000000023'::uuid, 'e1000000-0000-4000-8000-000000000002'::uuid, 'e1000000-0000-4000-8000-000000000001'::uuid, 'B-1 계곡 하부 도보조', '2026-05-19T12:46:00+09:00'::timestamptz),
        ('e3300000-0000-4000-8000-000000000024'::uuid, 'e1000000-0000-4000-8000-000000000003'::uuid, 'e1000000-0000-4000-8000-000000000001'::uuid, 'B-2 계곡 상부 도보조', '2026-05-19T12:46:00+09:00'::timestamptz),
        ('e3300000-0000-4000-8000-000000000025'::uuid, 'e1000000-0000-4000-8000-000000000008'::uuid, 'e1000000-0000-4000-8000-000000000007'::uuid, 'C-1 기동대 보조 수색', '2026-05-19T12:47:00+09:00'::timestamptz),
        ('e3300000-0000-4000-8000-000000000026'::uuid, 'e1000000-0000-4000-8000-000000000009'::uuid, 'e1000000-0000-4000-8000-000000000007'::uuid, 'C-2 능선 보조 수색', '2026-05-19T12:47:00+09:00'::timestamptz),
        ('e3300000-0000-4000-8000-000000000027'::uuid, 'e1000000-0000-4000-8000-000000000011'::uuid, 'e1000000-0000-4000-8000-000000000001'::uuid, 'D-1 경찰견 투입', '2026-05-19T12:48:00+09:00'::timestamptz),
        ('e3300000-0000-4000-8000-000000000028'::uuid, 'e1000000-0000-4000-8000-000000000019'::uuid, 'e1000000-0000-4000-8000-000000000001'::uuid, 'D-2 통신지원반 동행', '2026-05-19T12:48:00+09:00'::timestamptz)
) AS seed(area_id, account_id, assigned_by, memo, assigned_at)
ON CONFLICT (id) DO UPDATE SET
    search_area_id = EXCLUDED.search_area_id,
    assigned_account_id = EXCLUDED.assigned_account_id,
    assigned_by_account_id = EXCLUDED.assigned_by_account_id,
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
SELECT
    ('e3400000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    CASE WHEN gs <= 4 THEN 'e3200000-0000-4000-8000-000000000002'::uuid ELSE 'e3200000-0000-4000-8000-000000000003'::uuid END,
    ('e3100000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    ('e2000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    CASE WHEN gs IN (3, 4, 8, 13) THEN 'ENDED' ELSE 'ACTIVE' END,
    CASE WHEN gs IN (1, 2, 3, 4, 5, 6) THEN 'e1000000-0000-4000-8000-000000000001'::uuid ELSE 'e1000000-0000-4000-8000-000000000007'::uuid END,
    CASE WHEN gs IN (3, 4, 8, 13) THEN 'e1000000-0000-4000-8000-000000000001'::uuid ELSE NULL END,
    CASE WHEN gs <= 4 THEN '2026-05-19T10:20:00+09:00'::timestamptz + (gs || ' minutes')::interval ELSE '2026-05-19T12:45:00+09:00'::timestamptz + (gs || ' minutes')::interval END,
    CASE WHEN gs IN (3, 4) THEN '2026-05-19T12:05:00+09:00'::timestamptz + (gs || ' minutes')::interval WHEN gs IN (8, 13) THEN '2026-05-19T13:08:00+09:00'::timestamptz + (gs || ' minutes')::interval ELSE NULL END,
    gs + 1,
    CASE WHEN gs <= 4 THEN '2026-05-19T10:20:00+09:00'::timestamptz + (gs || ' minutes')::interval ELSE '2026-05-19T12:45:00+09:00'::timestamptz + (gs || ' minutes')::interval END,
    CURRENT_TIMESTAMP
FROM generate_series(1, 16) AS gs
ON CONFLICT (id) DO UPDATE SET
    operational_period_id = EXCLUDED.operational_period_id,
    incident_assignment_id = EXCLUDED.incident_assignment_id,
    police_phone_id = EXCLUDED.police_phone_id,
    status = EXCLUDED.status,
    ended_by_account_id = EXCLUDED.ended_by_account_id,
    ended_at = EXCLUDED.ended_at,
    version = EXCLUDED.version,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO search_path (
    id,
    duty_shift_id,
    account_id,
    status,
    started_at,
    ended_at,
    geometry,
    version,
    created_at,
    updated_at
)
SELECT
    seed.id::uuid,
    seed.duty_shift_id::uuid,
    ia.account_id,
    seed.status,
    seed.started_at::timestamptz,
    seed.ended_at::timestamptz,
    seed.geometry,
    seed.version,
    seed.created_at::timestamptz,
    seed.updated_at::timestamptz
FROM (
    VALUES
    ('e3500000-0000-4000-8000-000000000001', 'e3400000-0000-4000-8000-000000000001', 'ENDED', '2026-05-19T10:24:00+09:00', '2026-05-19T11:35:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(126.9910 35.1450,126.9950 35.1485,127.0000 35.1515,127.0060 35.1550)'), 4326), 2, '2026-05-19T10:24:00+09:00', '2026-05-19T11:35:00+09:00'),
    ('e3500000-0000-4000-8000-000000000002', 'e3400000-0000-4000-8000-000000000002', 'ENDED', '2026-05-19T10:26:00+09:00', '2026-05-19T12:12:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(126.9990 35.1460,127.0040 35.1510,127.0100 35.1560,127.0140 35.1600)'), 4326), 2, '2026-05-19T10:26:00+09:00', '2026-05-19T12:12:00+09:00'),
    ('e3500000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000005', 'RECORDING', '2026-05-19T12:52:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.9890 35.1450,126.9930 35.1490,126.9965 35.1510,126.9985 35.1530)'), 4326), 4, '2026-05-19T12:52:00+09:00', '2026-05-19T13:14:00+09:00'),
    ('e3500000-0000-4000-8000-000000000004', 'e3400000-0000-4000-8000-000000000006', 'RECORDING', '2026-05-19T12:53:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.9960 35.1455,126.9990 35.1500,127.0020 35.1530,127.0050 35.1565)'), 4326), 3, '2026-05-19T12:53:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3500000-0000-4000-8000-000000000005', 'e3400000-0000-4000-8000-000000000007', 'PAUSED', '2026-05-19T12:54:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(127.0010 35.1480,127.0060 35.1530,127.0110 35.1585)'), 4326), 4, '2026-05-19T12:54:00+09:00', '2026-05-19T13:12:00+09:00'),
    ('e3500000-0000-4000-8000-000000000006', 'e3400000-0000-4000-8000-000000000009', 'RECORDING', '2026-05-19T12:56:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.9910 35.1610,126.9960 35.1650,126.9990 35.1700)'), 4326), 3, '2026-05-19T12:56:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3500000-0000-4000-8000-000000000007', 'e3400000-0000-4000-8000-000000000010', 'RECORDING', '2026-05-19T12:58:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(127.0080 35.1600,127.0130 35.1640,127.0175 35.1680)'), 4326), 2, '2026-05-19T12:58:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3500000-0000-4000-8000-000000000008', 'e3400000-0000-4000-8000-000000000011', 'ENDED', '2026-05-19T12:59:00+09:00', '2026-05-19T13:11:00+09:00', ST_SetSRID(ST_GeomFromText('LINESTRING(127.0150 35.1590,127.0200 35.1630,127.0220 35.1690)'), 4326), 2, '2026-05-19T12:59:00+09:00', '2026-05-19T13:11:00+09:00'),
    ('e3500000-0000-4000-8000-000000000009', 'e3400000-0000-4000-8000-000000000012', 'RECORDING', '2026-05-19T13:00:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(126.9850 35.1590,126.9900 35.1640,126.9940 35.1690)'), 4326), 2, '2026-05-19T13:00:00+09:00', '2026-05-19T13:15:00+09:00'),
    ('e3500000-0000-4000-8000-000000000010', 'e3400000-0000-4000-8000-000000000014', 'RECORDING', '2026-05-19T13:03:00+09:00', NULL, ST_SetSRID(ST_GeomFromText('LINESTRING(127.0180 35.1600,127.0210 35.1650,127.0235 35.1710)'), 4326), 2, '2026-05-19T13:03:00+09:00', '2026-05-19T13:15:00+09:00')
) AS seed(id, duty_shift_id, status, started_at, ended_at, geometry, version, created_at, updated_at)
JOIN duty_shift ds ON ds.id = seed.duty_shift_id::uuid
JOIN incident_assignment ia ON ia.id = ds.incident_assignment_id
ON CONFLICT (id) DO UPDATE SET
    duty_shift_id = EXCLUDED.duty_shift_id,
    account_id = EXCLUDED.account_id,
    status = EXCLUDED.status,
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
SELECT
    ('e3600000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    ('e3500000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    CASE WHEN gs IN (1, 2, 8, 10) THEN 'VEHICLE' ELSE 'FOOT' END,
    CASE WHEN gs = 10 THEN 'MANUAL' ELSE 'AUTO' END,
    geometry,
    started_at,
    COALESCE(ended_at, started_at + INTERVAL '35 minutes'),
    CASE WHEN gs = 10 THEN 'e1000000-0000-4000-8000-000000000001'::uuid ELSE NULL END,
    CASE WHEN gs = 10 THEN '2026-05-19T13:12:00+09:00'::timestamptz ELSE NULL END,
    version,
    created_at,
    updated_at
FROM search_path sp
JOIN generate_series(1, 10) AS gs
  ON sp.id = ('e3500000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid
ON CONFLICT (id) DO UPDATE SET
    movement_type = EXCLUDED.movement_type,
    movement_type_source = EXCLUDED.movement_type_source,
    geometry = EXCLUDED.geometry,
    ended_at = EXCLUDED.ended_at,
    corrected_by_account_id = EXCLUDED.corrected_by_account_id,
    corrected_at = EXCLUDED.corrected_at,
    updated_at = EXCLUDED.updated_at;

INSERT INTO search_path_lifecycle_event (
    id,
    search_path_id,
    event_type,
    client_ts,
    server_received_at,
    version,
    created_at
)
SELECT
    ('e3510000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    sp.id,
    'STARTED',
    sp.started_at,
    sp.created_at,
    1,
    sp.created_at
FROM search_path sp
JOIN generate_series(1, 10) AS gs
  ON sp.id = ('e3500000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid
ON CONFLICT (id) DO NOTHING;

INSERT INTO search_path_lifecycle_event (
    id,
    search_path_id,
    event_type,
    client_ts,
    server_received_at,
    version,
    created_at
)
SELECT
    ('e3520000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    sp.id,
    CASE WHEN sp.status = 'PAUSED' THEN 'PAUSED' ELSE 'ENDED' END,
    COALESCE(sp.ended_at, sp.updated_at),
    sp.updated_at,
    sp.version,
    sp.updated_at
FROM search_path sp
JOIN generate_series(1, 10) AS gs
  ON sp.id = ('e3500000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid
WHERE sp.status IN ('PAUSED', 'ENDED')
ON CONFLICT (id) DO NOTHING;

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
    ('e3700000-0000-4000-8000-000000000001', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000002', 'e3400000-0000-4000-8000-000000000001', 'CLUE', NULL, ST_SetSRID(ST_MakePoint(126.9995, 35.1512), 4326), '등산로 옆 벤치 아래에서 회색 등산모와 유사한 천 조각 확인', '2026-05-19T10:58:00+09:00', 'e1000000-0000-4000-8000-000000000002', 'e2000000-0000-4000-8000-000000000002', 'APP', 'ACTIVE', 2, '2026-05-19T10:58:00+09:00', '2026-05-19T11:04:00+09:00'),
    ('e3700000-0000-4000-8000-000000000002', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000002', NULL, 'NOTE', NULL, ST_SetSRID(ST_MakePoint(126.9920, 35.1460), 4326), '상가 CCTV에서 07:41 북동 방향 단독 이동 확인', '2026-05-19T11:20:00+09:00', 'e1000000-0000-4000-8000-000000000018', NULL, 'WEB', 'ACTIVE', 1, '2026-05-19T11:20:00+09:00', '2026-05-19T11:20:00+09:00'),
    ('e3700000-0000-4000-8000-000000000003', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000005', 'FIELD_CONDITION', NULL, ST_SetSRID(ST_MakePoint(126.9975, 35.1525), 4326), '낙엽이 깊고 암반이 젖어 미끄러움. 들것 이동 어려움.', '2026-05-19T12:58:00+09:00', 'e1000000-0000-4000-8000-000000000005', 'e2000000-0000-4000-8000-000000000005', 'APP', 'ACTIVE', 1, '2026-05-19T12:58:00+09:00', '2026-05-19T12:58:00+09:00'),
    ('e3700000-0000-4000-8000-000000000004', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000007', 'SUPPORT_REQUEST', 'DRONE', ST_SetSRID(ST_MakePoint(127.0105, 35.1580), 4326), '계곡 상부 수목 밀집 구간 드론 열화상 확인 요청', '2026-05-19T13:02:00+09:00', 'e1000000-0000-4000-8000-000000000007', 'e2000000-0000-4000-8000-000000000007', 'APP', 'ACTIVE', 1, '2026-05-19T13:02:00+09:00', '2026-05-19T13:02:00+09:00'),
    ('e3700000-0000-4000-8000-000000000005', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000010', 'SUPPORT_REQUEST', 'POLICE_DOG', ST_SetSRID(ST_MakePoint(127.0148, 35.1642), 4326), '약수터 주변에서 방향 전환 가능성 있어 경찰견 투입 요청', '2026-05-19T13:05:00+09:00', 'e1000000-0000-4000-8000-000000000011', 'e2000000-0000-4000-8000-000000000011', 'APP', 'ACTIVE', 1, '2026-05-19T13:05:00+09:00', '2026-05-19T13:05:00+09:00'),
    ('e3700000-0000-4000-8000-000000000006', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000008', 'CLUE', NULL, ST_SetSRID(ST_MakePoint(126.9938, 35.1668), 4326), '능선 갈림길 표지목 아래 검은 배낭끈 유사 물품 발견', '2026-05-19T13:07:00+09:00', 'e1000000-0000-4000-8000-000000000008', 'e2000000-0000-4000-8000-000000000008', 'APP', 'ACTIVE', 1, '2026-05-19T13:07:00+09:00', '2026-05-19T13:07:00+09:00'),
    ('e3700000-0000-4000-8000-000000000007', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000012', 'FIELD_CONDITION', NULL, ST_SetSRID(ST_MakePoint(126.9875, 35.1645), 4326), '서측 사면 통신 약함. 무전 중계 단말 동행 필요.', '2026-05-19T13:08:00+09:00', 'e1000000-0000-4000-8000-000000000012', 'e2000000-0000-4000-8000-000000000012', 'APP', 'ACTIVE', 1, '2026-05-19T13:08:00+09:00', '2026-05-19T13:08:00+09:00'),
    ('e3700000-0000-4000-8000-000000000008', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000014', 'SUPPORT_REQUEST', 'OTHER', ST_SetSRID(ST_MakePoint(127.0212, 35.1662), 4326), '임도 교차로 야간 조명, 보조 배터리, 안전로프 추가 요청', '2026-05-19T13:10:00+09:00', 'e1000000-0000-4000-8000-000000000019', 'e2000000-0000-4000-8000-000000000019', 'APP', 'ACTIVE', 1, '2026-05-19T13:10:00+09:00', '2026-05-19T13:10:00+09:00'),
    ('e3700000-0000-4000-8000-000000000009', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', NULL, 'NOTE', NULL, ST_SetSRID(ST_MakePoint(127.0022, 35.1548), 4326), '보호자 진술: 평소 약수터 방향보다 꼬막재 방향 산책 선호', '2026-05-19T13:12:00+09:00', 'e1000000-0000-4000-8000-000000000017', NULL, 'WEB', 'ACTIVE', 1, '2026-05-19T13:12:00+09:00', '2026-05-19T13:12:00+09:00'),
    ('e3700000-0000-4000-8000-000000000010', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000010', 'PERSON_FOUND', NULL, ST_SetSRID(ST_MakePoint(127.0168, 35.1658), 4326), '유사 인상착의 등산객 발견 신고 접수. 신원 확인 중.', '2026-05-19T13:13:00+09:00', 'e1000000-0000-4000-8000-000000000011', 'e2000000-0000-4000-8000-000000000011', 'APP', 'ACTIVE', 1, '2026-05-19T13:13:00+09:00', '2026-05-19T13:13:00+09:00'),
    ('e3700000-0000-4000-8000-000000000011', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', 'e3400000-0000-4000-8000-000000000015', 'FIELD_CONDITION', NULL, ST_SetSRID(ST_MakePoint(127.0045, 35.1710), 4326), '북측 능선 바람 강함. 드론 고도 제한 필요.', '2026-05-19T13:14:00+09:00', 'e1000000-0000-4000-8000-000000000014', 'e2000000-0000-4000-8000-000000000014', 'APP', 'ACTIVE', 1, '2026-05-19T13:14:00+09:00', '2026-05-19T13:14:00+09:00'),
    ('e3700000-0000-4000-8000-000000000012', 'e3000000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000003', NULL, 'NOTE', NULL, ST_SetSRID(ST_MakePoint(126.9905, 35.1455), 4326), '교통통제반 원효사 입구 차량 진입 제한 시작', '2026-05-19T13:15:00+09:00', 'e1000000-0000-4000-8000-000000000015', NULL, 'WEB', 'ACTIVE', 1, '2026-05-19T13:15:00+09:00', '2026-05-19T13:15:00+09:00')
ON CONFLICT (id) DO UPDATE SET
    operational_period_id = EXCLUDED.operational_period_id,
    duty_shift_id = EXCLUDED.duty_shift_id,
    marker_type = EXCLUDED.marker_type,
    support_request_type = EXCLUDED.support_request_type,
    location = EXCLUDED.location,
    memo = EXCLUDED.memo,
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
    ('e3710000-0000-4000-8000-000000000001', 'e3700000-0000-4000-8000-000000000001', 'mock-upload/markers/test3-grey-cap-fragment.jpg', 'ATTACHED', '2026-05-19T11:03:00+09:00', 'image/jpeg', 728144, 1440, 1080, repeat('1', 64), '2026-05-19T10:59:00+09:00', NULL, 2, '2026-05-19T10:59:00+09:00', '2026-05-19T11:03:00+09:00', NULL),
    ('e3710000-0000-4000-8000-000000000002', 'e3700000-0000-4000-8000-000000000006', 'mock-upload/markers/test3-backpack-strap.jpg', 'ATTACHED', '2026-05-19T13:08:00+09:00', 'image/jpeg', 612992, 1280, 960, repeat('2', 64), '2026-05-19T13:07:00+09:00', NULL, 1, '2026-05-19T13:07:00+09:00', '2026-05-19T13:08:00+09:00', NULL),
    ('e3710000-0000-4000-8000-000000000003', 'e3700000-0000-4000-8000-000000000010', 'mock-upload/markers/test3-person-check.jpg', 'ATTACHED', '2026-05-19T13:14:00+09:00', 'image/jpeg', 845102, 1600, 1200, repeat('3', 64), '2026-05-19T13:13:00+09:00', NULL, 1, '2026-05-19T13:13:00+09:00', '2026-05-19T13:14:00+09:00', NULL)
ON CONFLICT (id) DO UPDATE SET
    marker_id = EXCLUDED.marker_id,
    object_key = EXCLUDED.object_key,
    status = EXCLUDED.status,
    attached_at = EXCLUDED.attached_at,
    updated_at = EXCLUDED.updated_at;

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
    ('e3720000-0000-4000-8000-000000000001', 'e3700000-0000-4000-8000-000000000004', 'SUPPORT_REQUEST_CREATED', 'COMMANDERS_AND_FIELD_COMMANDERS', ARRAY['e1000000-0000-4000-8000-000000000001'::uuid, 'e1000000-0000-4000-8000-000000000004'::uuid, 'e1000000-0000-4000-8000-000000000007'::uuid], ARRAY['e2000000-0000-4000-8000-000000000001'::uuid, 'e2000000-0000-4000-8000-000000000004'::uuid, 'e2000000-0000-4000-8000-000000000007'::uuid], jsonb_build_object('incidentId', 'e3000000-0000-4000-8000-000000000001', 'markerId', 'e3700000-0000-4000-8000-000000000004', 'markerType', 'SUPPORT_REQUEST', 'supportRequestType', 'DRONE', 'memo', '계곡 상부 수목 밀집 구간 드론 열화상 확인 요청'), 'SNAPSHOT_CREATED', 1, '2026-05-19T13:02:00+09:00'),
    ('e3720000-0000-4000-8000-000000000002', 'e3700000-0000-4000-8000-000000000005', 'SUPPORT_REQUEST_CREATED', 'COMMANDERS_AND_FIELD_COMMANDERS', ARRAY['e1000000-0000-4000-8000-000000000001'::uuid, 'e1000000-0000-4000-8000-000000000007'::uuid, 'e1000000-0000-4000-8000-000000000012'::uuid], ARRAY['e2000000-0000-4000-8000-000000000001'::uuid, 'e2000000-0000-4000-8000-000000000007'::uuid, 'e2000000-0000-4000-8000-000000000012'::uuid], jsonb_build_object('incidentId', 'e3000000-0000-4000-8000-000000000001', 'markerId', 'e3700000-0000-4000-8000-000000000005', 'markerType', 'SUPPORT_REQUEST', 'supportRequestType', 'POLICE_DOG', 'memo', '약수터 주변에서 방향 전환 가능성 있어 경찰견 투입 요청'), 'SNAPSHOT_CREATED', 1, '2026-05-19T13:05:00+09:00'),
    ('e3720000-0000-4000-8000-000000000003', 'e3700000-0000-4000-8000-000000000010', 'PERSON_FOUND', 'ALL_INCIDENT_ASSIGNED', ARRAY(SELECT ('e1000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid FROM generate_series(1, 20) AS gs), ARRAY(SELECT ('e2000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid FROM generate_series(1, 20) AS gs), jsonb_build_object('incidentId', 'e3000000-0000-4000-8000-000000000001', 'markerId', 'e3700000-0000-4000-8000-000000000010', 'markerType', 'PERSON_FOUND', 'memo', '유사 인상착의 등산객 발견 신고 접수. 신원 확인 중.'), 'SNAPSHOT_CREATED', 1, '2026-05-19T13:13:00+09:00')
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
    ('e3800000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000001', 'OPERATIONAL_PERIOD', 'e3200000-0000-4000-8000-000000000001', '초동 탐문에서 원효사 입구 CCTV 07:41 이동 장면 확인. OP2는 꼬막재 방향 계곡부를 우선 확인.', 'e1000000-0000-4000-8000-000000000004', NULL, 'ACTIVE', 1, '2026-05-19T10:03:00+09:00', '2026-05-19T10:03:00+09:00'),
    ('e3800000-0000-4000-8000-000000000002', 'e3200000-0000-4000-8000-000000000002', 'OPERATIONAL_PERIOD', 'e3200000-0000-4000-8000-000000000002', 'OP2에서 모자 조각 추정 단서 확인. OP3는 북동 능선, 약수터, 통신 음영 지역까지 확대.', 'e1000000-0000-4000-8000-000000000001', NULL, 'ACTIVE', 2, '2026-05-19T12:24:00+09:00', '2026-05-19T12:24:00+09:00'),
    ('e3800000-0000-4000-8000-000000000003', 'e3200000-0000-4000-8000-000000000003', 'SEARCH_AREA', 'e3300000-0000-4000-8000-000000000028', 'D-2는 무전이 약해 통신지원반과 2인 1조로만 진입. 야간 조명 요청 완료.', 'e1000000-0000-4000-8000-000000000019', 'e3400000-0000-4000-8000-000000000014', 'ACTIVE', 1, '2026-05-19T13:11:00+09:00', '2026-05-19T13:11:00+09:00'),
    ('e3800000-0000-4000-8000-000000000004', 'e3200000-0000-4000-8000-000000000003', 'MARKER', 'e3700000-0000-4000-8000-000000000006', '배낭끈 유사 물품은 사진 첨부 완료. 해당 지점 주변 반경 80m를 다시 훑는 중.', 'e1000000-0000-4000-8000-000000000008', 'e3400000-0000-4000-8000-000000000008', 'ACTIVE', 1, '2026-05-19T13:09:00+09:00', '2026-05-19T13:09:00+09:00'),
    ('e3800000-0000-4000-8000-000000000005', 'e3200000-0000-4000-8000-000000000003', 'DUTY_SHIFT', 'e3400000-0000-4000-8000-000000000010', '경찰견 투입조는 약수터 주변 냄새 반응 확인 중. 무리한 하산 유도 금지.', 'e1000000-0000-4000-8000-000000000011', 'e3400000-0000-4000-8000-000000000010', 'ACTIVE', 1, '2026-05-19T13:12:00+09:00', '2026-05-19T13:12:00+09:00'),
    ('e3800000-0000-4000-8000-000000000006', 'e3200000-0000-4000-8000-000000000003', 'SEARCH_PATH', 'e3500000-0000-4000-8000-000000000005', 'B-2 경로는 일시정지 상태. 드론 확인 후 도보조 재진입 판단 필요.', 'e1000000-0000-4000-8000-000000000007', 'e3400000-0000-4000-8000-000000000007', 'ACTIVE', 1, '2026-05-19T13:13:00+09:00', '2026-05-19T13:13:00+09:00'),
    ('e3800000-0000-4000-8000-000000000007', 'e3200000-0000-4000-8000-000000000003', 'MARKER', 'e3700000-0000-4000-8000-000000000010', '유사 인상착의 발견 신고는 오인 가능성 있음. 신분 확인 전 발견 확정 처리 금지.', 'e1000000-0000-4000-8000-000000000001', NULL, 'ACTIVE', 1, '2026-05-19T13:14:00+09:00', '2026-05-19T13:14:00+09:00'),
    ('e3800000-0000-4000-8000-000000000008', 'e3200000-0000-4000-8000-000000000003', 'OPERATIONAL_PERIOD', 'e3200000-0000-4000-8000-000000000003', '17시 이후 기온 저하 예상. C/D구역은 야간 전환 전 안전로프와 랜턴 보급 상태 확인.', 'e1000000-0000-4000-8000-000000000016', NULL, 'ACTIVE', 1, '2026-05-19T13:15:00+09:00', '2026-05-19T13:15:00+09:00')
ON CONFLICT (id) DO UPDATE SET
    content = EXCLUDED.content,
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
    ('e3900000-0000-4000-8000-000000000001', 'e3200000-0000-4000-8000-000000000001', NULL, 'READY', 'OP1은 신고 지점 주변 CCTV, 상가 탐문, 원효사 입구 주차장 확인으로 진행됐다. 실종자는 07:41 북동 방향으로 이동한 것으로 추정된다.', repeat('4', 64), 'READY', 'e1000000-0000-4000-8000-000000000004', '2026-05-19T10:04:00+09:00', 1, '2026-05-19T10:03:00+09:00', '2026-05-19T10:04:00+09:00'),
    ('e3900000-0000-4000-8000-000000000002', 'e3200000-0000-4000-8000-000000000002', NULL, 'READY', 'OP2는 꼬막재 방향 계곡부 중심으로 수색했다. 모자 조각 추정 단서와 CCTV 진술을 근거로 OP3 확대 수색이 결정됐다.', repeat('5', 64), 'READY', 'e1000000-0000-4000-8000-000000000001', '2026-05-19T12:27:00+09:00', 1, '2026-05-19T12:25:00+09:00', '2026-05-19T12:27:00+09:00'),
    ('e3900000-0000-4000-8000-000000000003', 'e3200000-0000-4000-8000-000000000003', NULL, 'GENERATING', NULL, repeat('6', 64), 'PENDING_SYNC', 'e1000000-0000-4000-8000-000000000001', NULL, 1, '2026-05-19T13:15:00+09:00', '2026-05-19T13:15:00+09:00')
ON CONFLICT (id) DO UPDATE SET
    generation_status = EXCLUDED.generation_status,
    content = EXCLUDED.content,
    source_data_hash = EXCLUDED.source_data_hash,
    source_readiness = EXCLUDED.source_readiness,
    generated_at = EXCLUDED.generated_at,
    updated_at = EXCLUDED.updated_at;

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
VALUES (
    'e3a00000-0000-4000-8000-000000000001',
    'e3000000-0000-4000-8000-000000000001',
    3,
    'e3200000-0000-4000-8000-000000000003',
    'e3300000-0000-4000-8000-000000000003',
    5,
    repeat('7', 64),
    1,
    jsonb_build_object(
        'incidentId', 'e3000000-0000-4000-8000-000000000001',
        'opId', 'e3200000-0000-4000-8000-000000000003',
        'manifestVersion', 3,
        'areaCount', 13,
        'markerCount', 12,
        'pathCount', 10,
        'tileRegion', jsonb_build_object('name', 'gwangju-mudeungsan-wonhyosa', 'minZoom', 13, 'maxZoom', 17)
    ),
    '2026-05-20T13:00:00+09:00',
    '2026-05-19T12:36:00+09:00',
    '2026-05-19T13:15:00+09:00'
)
ON CONFLICT (id) DO UPDATE SET
    manifest_version = EXCLUDED.manifest_version,
    operational_period_id = EXCLUDED.operational_period_id,
    overall_search_area_id = EXCLUDED.overall_search_area_id,
    overall_search_area_version = EXCLUDED.overall_search_area_version,
    manifest_hash = EXCLUDED.manifest_hash,
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
SELECT
    ('e3b00000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    'e3a00000-0000-4000-8000-000000000001',
    ('e2000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    ('e1000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
    CASE
        WHEN gs IN (6, 13) THEN 'FAILED'
        WHEN gs IN (9, 16) THEN 'STALE'
        WHEN gs IN (10, 19) THEN 'PARTIAL'
        WHEN gs = 20 THEN 'NOT_STARTED'
        ELSE 'READY'
    END,
    48,
    CASE
        WHEN gs IN (6, 13) THEN 21
        WHEN gs IN (10, 19) THEN 39
        WHEN gs = 20 THEN 0
        ELSE 48
    END,
    CASE
        WHEN gs IN (6, 13) THEN 4
        WHEN gs IN (10, 19) THEN 2
        ELSE 0
    END,
    CASE
        WHEN gs IN (6, 13) THEN ARRAY['tiles:mudeungsan:16/55740/25341', 'manifest:checksum']::text[]
        WHEN gs IN (10, 19) THEN ARRAY['tiles:mudeungsan:17/111481/50682']::text[]
        ELSE ARRAY[]::text[]
    END,
    CASE
        WHEN gs IN (6, 13) THEN 'storage_write_failed'
        WHEN gs IN (9, 16) THEN 'manifest_version_outdated'
        WHEN gs IN (10, 19) THEN 'radio_link_unstable'
        ELSE NULL
    END,
    CASE WHEN gs = 20 THEN NULL ELSE '2026-05-19T13:00:00+09:00'::timestamptz + (gs || ' minutes')::interval END,
    gs,
    '2026-05-19T12:36:00+09:00',
    CURRENT_TIMESTAMP
FROM generate_series(1, 20) AS gs
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
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
