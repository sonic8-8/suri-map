BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '30s';
SELECT json_build_object(
  'incidentId', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
  'opId', '88888888-8888-8888-8888-888888880001',
  'paths', COALESCE(json_agg(json_build_object(
    'id', p.id, 'accountId', p.account_id, 'opId', o.id,
    'version', p.version::text, 'status', p.status,
    'segments', (SELECT COALESCE(json_agg(json_build_object(
      'id', s.id, 'version', s.version::text,
      'startPointOrder', s.start_point_order, 'endPointOrder', s.end_point_order,
      'movementType', s.movement_type,
      'coordinates', (SELECT json_agg(json_build_array(g.lon, g.lat) ORDER BY g.point_order)
        FROM search_path_gps_point g WHERE g.search_path_id = p.id
        AND g.point_order BETWEEN s.start_point_order AND s.end_point_order)
    ) ORDER BY s.start_point_order, s.id), '[]'::json)
    FROM search_path_segment s WHERE s.search_path_id = p.id)
  ) ORDER BY p.id), '[]'::json)
)
FROM search_path p
JOIN duty_shift d ON d.id = p.duty_shift_id
JOIN operational_period o ON o.id = d.operational_period_id
WHERE o.incident_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'
  AND o.id = '88888888-8888-8888-8888-888888880001';
COMMIT;
