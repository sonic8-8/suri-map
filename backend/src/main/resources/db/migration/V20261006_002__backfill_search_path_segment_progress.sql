-- Stop application/test writers and take a backup before this transactional migration.
-- Do not infer missing GPS points or reset existing path/segment versions.
LOCK TABLE search_path, search_path_segment, search_path_gps_point
    IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE search_path_segment_ranges ON COMMIT DROP AS
WITH shapes AS MATERIALIZED (
    SELECT s.*, ST_NPoints(geometry) AS size,
        ST_X(ST_StartPoint(geometry)) AS first_x, ST_Y(ST_StartPoint(geometry)) AS first_y,
        ST_X(ST_EndPoint(geometry)) AS last_x, ST_Y(ST_EndPoint(geometry)) AS last_y
    FROM search_path_segment s
), candidates AS MATERIALIZED (
    SELECT s.id, s.search_path_id, s.geometry, g.point_order AS start_order,
        g.point_order + length.value - 1 AS end_order, length.value AS point_count
    FROM shapes s
    CROSS JOIN LATERAL (
        SELECT s.size AS value
        UNION ALL
        SELECT 1 WHERE s.size = 2 AND s.first_x = s.last_x AND s.first_y = s.last_y
            AND s.started_at = s.ended_at
    ) length
    JOIN search_path_gps_point g ON g.search_path_id = s.search_path_id
        AND g.client_ts = s.started_at
        AND g.lon::double precision = s.first_x AND g.lat::double precision = s.first_y
    JOIN search_path_gps_point e ON e.search_path_id = s.search_path_id
        AND e.point_order = g.point_order + length.value - 1 AND e.client_ts = s.ended_at
        AND e.lon::double precision = s.last_x AND e.lat::double precision = s.last_y
), matched AS MATERIALIZED (
    SELECT c.id, c.search_path_id, c.start_order, c.end_order,
        md5('search-path-segment:' || c.search_path_id || ':' || c.start_order || ':' || c.end_order)
            AS app_hash
    FROM candidates c
    WHERE NOT EXISTS (
        SELECT 1 FROM generate_series(0, c.point_count - 1) AS position(value)
        LEFT JOIN search_path_gps_point g ON g.search_path_id = c.search_path_id
            AND g.point_order = c.start_order + position.value
        WHERE g.point_order IS NULL
            OR g.lon::double precision <> ST_X(ST_PointN(c.geometry, position.value + 1))
            OR g.lat::double precision <> ST_Y(ST_PointN(c.geometry, position.value + 1))
    )
), identified AS MATERIALIZED (
    -- UUID.nameUUIDFromBytes uses MD5 with UUID version/variant bits replaced.
    SELECT m.*, id = overlay(overlay(app_hash placing '3' from 13 for 1)
        placing substr('89ab', ((strpos('0123456789abcdef', substr(app_hash, 17, 1)) - 1) % 4) + 1, 1)
        from 17 for 1)::uuid AS app_id_matches
    FROM matched m
)
SELECT id, search_path_id, start_order, end_order FROM (
    -- Window counts avoid a quadratic self-join on repeated coordinate candidates.
    SELECT i.*, count(*) OVER (PARTITION BY id) AS range_count,
        count(*) FILTER (WHERE app_id_matches) OVER (PARTITION BY id) AS app_count
    FROM identified i
) counted
WHERE range_count = 1 OR (app_count = 1 AND app_id_matches);

ALTER TABLE search_path_segment_ranges ADD PRIMARY KEY (id);

CREATE TEMP TABLE search_path_progress_validation (
    ranges_resolved boolean NOT NULL CHECK (ranges_resolved),
    gps_fully_covered boolean NOT NULL CHECK (gps_fully_covered),
    gps_order_contiguous boolean NOT NULL CHECK (gps_order_contiguous),
    existing_progress_consistent boolean NOT NULL CHECK (existing_progress_consistent),
    no_geometry_without_gps boolean NOT NULL CHECK (no_geometry_without_gps)
) ON COMMIT DROP;

WITH coverage AS MATERIALIZED (
    SELECT search_path_id, point_order, count(*) AS assignments
    FROM search_path_segment_ranges
    CROSS JOIN LATERAL generate_series(start_order, end_order) AS points(point_order)
    GROUP BY search_path_id, point_order
)
INSERT INTO search_path_progress_validation
SELECT
    NOT EXISTS (
        SELECT 1 FROM search_path_segment s
        LEFT JOIN search_path_segment_ranges r ON r.id = s.id
        LEFT JOIN search_path p ON p.id = s.search_path_id
        WHERE r.id IS NULL OR p.id IS NULL
    ),
    NOT EXISTS (
        SELECT 1 FROM search_path_gps_point g
        FULL JOIN coverage c USING (search_path_id, point_order)
        WHERE g.point_order IS NULL OR c.assignments IS DISTINCT FROM 1::bigint
    ),
    NOT EXISTS (
        SELECT search_path_id FROM search_path_gps_point
        GROUP BY search_path_id HAVING min(point_order) <> 0 OR count(*) <> max(point_order)::bigint + 1
    ),
    NOT EXISTS (
        SELECT 1 FROM search_path_segment s
        JOIN search_path_segment_ranges r ON r.id = s.id
        JOIN search_path p ON p.id = s.search_path_id
        WHERE (s.start_point_order IS NOT NULL AND s.start_point_order <> r.start_order)
            OR (s.end_point_order IS NOT NULL AND s.end_point_order <> r.end_order)
            OR s.last_changed_path_version > p.version
    ),
    NOT EXISTS (
        SELECT 1 FROM search_path p
        WHERE p.geometry IS NOT NULL AND NOT ST_IsEmpty(p.geometry)
            AND NOT EXISTS (SELECT 1 FROM search_path_gps_point g WHERE g.search_path_id = p.id)
    );

UPDATE search_path_segment s
SET start_point_order = r.start_order,
    end_point_order = r.end_order,
    last_changed_path_version = COALESCE(s.last_changed_path_version, p.version)
FROM search_path_segment_ranges r
JOIN search_path p ON p.id = r.search_path_id
WHERE s.id = r.id
    AND (s.start_point_order IS NULL OR s.end_point_order IS NULL OR s.last_changed_path_version IS NULL);
