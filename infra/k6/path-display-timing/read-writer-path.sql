BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '15s';
SELECT json_build_object(
  'incidentId', o.incident_id, 'opId', o.id, 'pathId', p.id,
  'accountId', p.account_id, 'policePhoneId', d.police_phone_id,
  'version', p.version::text, 'status', p.status, 'opStatus', o.status,
  'incidentStatus', i.status, 'dutyStatus', d.status,
  'registered', pp.registered, 'phoneAccountId', pp.account_id,
  'assignmentActive', a.revoked_at IS NULL AND a.account_id = p.account_id,
  'pointCount', (SELECT count(*) FROM search_path_gps_point WHERE search_path_id = p.id),
  'recentPoints', (SELECT json_agg(row_to_json(t) ORDER BY t."pointOrder") FROM (
    SELECT point_id AS "pointId", point_order AS "pointOrder", lon, lat,
      elapsed_realtime_nanos::text AS "elapsedRealtimeNanos", client_ts AS "clientTs"
    FROM search_path_gps_point WHERE search_path_id = p.id ORDER BY point_order DESC LIMIT 6
  ) t),
  'latestSegment', (SELECT row_to_json(s) FROM (
    SELECT id, version::text, last_changed_path_version::text AS "lastChangedPathVersion", start_point_order AS "startPointOrder",
      end_point_order AS "endPointOrder", movement_type AS "movementType"
    FROM search_path_segment WHERE search_path_id = p.id ORDER BY end_point_order DESC LIMIT 1
  ) s)
)
FROM search_path p
JOIN duty_shift d ON d.id = p.duty_shift_id
JOIN incident_assignment a ON a.id = d.incident_assignment_id
JOIN operational_period o ON o.id = d.operational_period_id
JOIN incident i ON i.id = o.incident_id
JOIN police_phone pp ON pp.id = d.police_phone_id
WHERE p.id = 'e1000000-0000-4000-8000-000000000001'
  AND o.id = '88888888-8888-8888-8888-888888880001'
  AND o.incident_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
COMMIT;
