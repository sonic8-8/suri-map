CREATE OR REPLACE FUNCTION surimap_fixture_uuid(value TEXT, namespace TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
SELECT CASE
    WHEN value IS NULL OR value = '' THEN NULL
    WHEN namespace = 'incident' AND value = 'inc-precinct-first-001' THEN 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid
    WHEN namespace = 'incident' AND value = 'inc-precinct-closed-001' THEN 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0012'::uuid
    WHEN namespace = 'operational_period' AND value = 'op-precinct-001-op1' THEN '88888888-8888-8888-8888-888888880001'::uuid
    WHEN namespace = 'operational_period' AND value = 'op-precinct-001-op2' THEN '88888888-8888-8888-8888-888888880002'::uuid
    WHEN namespace = 'search_area' AND value IN ('osa-precinct-001', 'osa-precinct-001-v1') THEN 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001'::uuid
    WHEN namespace = 'search_area' AND value IN ('area-precinct-a1', 'area-precinct-a1-v1') THEN 'cccccccc-cccc-cccc-cccc-cccccccc0001'::uuid
    WHEN namespace = 'offline_package_manifest' AND value = 'tile-manifest-inc-precinct-001' THEN '77777777-0000-4000-8000-000000000701'::uuid
    WHEN namespace = 'offline_package_installation' AND value = 'pkg-status-precinct-001' THEN '77777777-0000-4000-8000-000000000901'::uuid
    WHEN namespace = 'account' AND value = 'acct-precinct-cmd' THEN '11111111-1111-1111-1111-111111110001'::uuid
    WHEN namespace = 'account' AND value = 'acct-precinct-car' THEN '11111111-1111-1111-1111-111111110002'::uuid
    WHEN namespace = 'account' AND value = 'acct-precinct-team' THEN '11111111-1111-1111-1111-111111110003'::uuid
    WHEN namespace = 'account' AND value = 'acct-cmd-alpha' THEN '11111111-1111-1111-1111-111111110004'::uuid
    WHEN namespace = 'account' AND value = 'acct-team-alpha' THEN '11111111-1111-1111-1111-111111110005'::uuid
    WHEN namespace = 'account' AND value = 'acct-support-cmd' THEN '11111111-1111-1111-1111-111111110006'::uuid
    WHEN namespace = 'account' AND value = 'acct-support-car' THEN '11111111-1111-1111-1111-111111110007'::uuid
    WHEN namespace = 'account' AND value = 'acct-support-team' THEN '11111111-1111-1111-1111-111111110008'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-precinct-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000201'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-precinct-car-01' THEN '50000000-0000-0000-0000-000000000001'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-precinct-phone-01' THEN '00000000-0000-0000-0000-000000000101'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-alpha-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000204'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-alpha-phone-01' THEN '00000000-0000-0000-0000-000000000205'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-support-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000206'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-support-car-01' THEN '00000000-0000-0000-0000-000000000207'::uuid
    WHEN namespace = 'police_phone' AND value = 'dev-support-phone-01' THEN '00000000-0000-0000-0000-000000000208'::uuid
    WHEN value ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN value::uuid
    ELSE md5(namespace || ':' || value)::uuid
END
$$;

CREATE OR REPLACE FUNCTION surimap_fixture_uuid_array(input_values TEXT[], namespace TEXT)
RETURNS UUID[]
LANGUAGE SQL
IMMUTABLE
AS $$
SELECT COALESCE(
    ARRAY_AGG(surimap_fixture_uuid(value, namespace) ORDER BY ordinality),
    ARRAY[]::uuid[]
)
FROM UNNEST(input_values) WITH ORDINALITY AS source(value, ordinality)
$$;

ALTER TABLE offline_package_manifest
    ALTER COLUMN id TYPE UUID
    USING surimap_fixture_uuid(id::text, 'offline_package_manifest'),
    ALTER COLUMN incident_id TYPE UUID
    USING surimap_fixture_uuid(incident_id::text, 'incident'),
    ALTER COLUMN operational_period_id TYPE UUID
    USING surimap_fixture_uuid(operational_period_id::text, 'operational_period'),
    ALTER COLUMN overall_search_area_id TYPE UUID
    USING surimap_fixture_uuid(overall_search_area_id::text, 'search_area');

ALTER TABLE offline_package_installation
    ALTER COLUMN id TYPE UUID
    USING surimap_fixture_uuid(id::text, 'offline_package_installation'),
    ALTER COLUMN offline_package_manifest_id TYPE UUID
    USING surimap_fixture_uuid(offline_package_manifest_id::text, 'offline_package_manifest'),
    ALTER COLUMN police_phone_id TYPE UUID
    USING surimap_fixture_uuid(police_phone_id::text, 'police_phone'),
    ALTER COLUMN last_reported_by_account_id TYPE UUID
    USING surimap_fixture_uuid(last_reported_by_account_id::text, 'account');

ALTER TABLE marker_notification
    ALTER COLUMN recipient_account_ids DROP DEFAULT,
    ALTER COLUMN recipient_police_phone_ids DROP DEFAULT;

ALTER TABLE marker_notification
    ALTER COLUMN recipient_account_ids TYPE UUID[]
    USING surimap_fixture_uuid_array(recipient_account_ids, 'account'),
    ALTER COLUMN recipient_police_phone_ids TYPE UUID[]
    USING surimap_fixture_uuid_array(recipient_police_phone_ids, 'police_phone');

ALTER TABLE marker_notification
    ALTER COLUMN recipient_account_ids SET DEFAULT ARRAY[]::uuid[],
    ALTER COLUMN recipient_police_phone_ids SET DEFAULT ARRAY[]::uuid[];

DROP FUNCTION surimap_fixture_uuid_array(TEXT[], TEXT);
DROP FUNCTION surimap_fixture_uuid(TEXT, TEXT);
