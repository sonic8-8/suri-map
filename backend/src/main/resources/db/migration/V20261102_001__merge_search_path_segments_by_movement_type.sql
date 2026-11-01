-- 적용 전에 앱과 테스트의 쓰기를 멈추고 백업한다. 지워진 구간 ID는 되돌릴 수 없다.
-- 적용 뒤에는 지휘관이 열어 둔 상황판을 새로고침해야 한다. 새로고침하지 않은 상황판에서 지워진 구간의 시작 순번을 이어받기 순번으로 조회하면 거절된다.
-- 좌표 추가의 이어 붙이기 규칙(#33)과 같게, 순번이 바로 이어지는 같은 이동 유형의 AUTO 구간을 좌표 120개 안에서 앞에서부터 묶는다.
-- 원본 GPS 좌표와 제외 좌표는 바꾸지 않는다. 확정할 수 없는 자료가 있으면 CHECK 위반으로 전체를 롤백한다.
LOCK TABLE search_path, search_path_segment, search_path_gps_point
    IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE search_path_segment_merge_validation (
    rule text PRIMARY KEY,
    passed boolean NOT NULL CHECK (passed)
) ON COMMIT DROP;

CREATE TEMP TABLE search_path_segment_merge_order ON COMMIT DROP AS
SELECT s.id, s.search_path_id, s.start_point_order, s.end_point_order,
    s.end_point_order - s.start_point_order + 1 AS point_count,
    s.movement_type, s.movement_type_source,
    row_number() OVER (PARTITION BY s.search_path_id ORDER BY s.start_point_order, s.id) AS position
FROM search_path_segment s;

ALTER TABLE search_path_segment_merge_order ADD PRIMARY KEY (search_path_id, position);
ANALYZE search_path_segment_merge_order;

-- 묶을 구간을 정하기 전에 구간 범위가 GPS 순번 전체를 한 번씩 덮는지 확인한다.
WITH coverage AS MATERIALIZED (
    SELECT o.search_path_id, points.point_order, count(*) AS assignments
    FROM search_path_segment_merge_order o
    CROSS JOIN LATERAL generate_series(o.start_point_order, o.end_point_order) AS points(point_order)
    GROUP BY o.search_path_id, points.point_order
)
INSERT INTO search_path_segment_merge_validation
VALUES
    ('segment_progress_known', NOT EXISTS (
        SELECT 1 FROM search_path_segment s
        LEFT JOIN search_path p ON p.id = s.search_path_id
        WHERE p.id IS NULL OR s.start_point_order IS NULL OR s.end_point_order IS NULL
            OR s.last_changed_path_version IS NULL OR s.last_changed_path_version > p.version
    )),
    ('gps_order_contiguous', NOT EXISTS (
        SELECT search_path_id FROM search_path_gps_point
        GROUP BY search_path_id HAVING min(point_order) <> 0 OR count(*) <> max(point_order)::bigint + 1
    )),
    ('gps_covered_once', NOT EXISTS (
        SELECT 1 FROM search_path_gps_point g
        FULL JOIN coverage c USING (search_path_id, point_order)
        WHERE g.point_order IS NULL OR c.assignments IS DISTINCT FROM 1::bigint
    ));

-- 범위에 빈틈과 겹침이 없으므로 순서상 이웃한 구간은 순번이 바로 이어진다.
-- 묶음의 첫 구간과 이어 붙일 구간이 모두 AUTO이고 이동 유형이 같으며, 합친 좌표가 120개 이하일 때만 이어 붙인다.
CREATE TEMP TABLE search_path_segment_merge_members ON COMMIT DROP AS
WITH RECURSIVE walk AS (
    SELECT o.search_path_id, o.position, o.id, o.id AS head_id,
        o.movement_type AS group_type, o.movement_type_source = 'AUTO' AS group_auto,
        o.point_count AS group_point_count
    FROM search_path_segment_merge_order o
    WHERE o.position = 1
    UNION ALL
    SELECT o.search_path_id, o.position, o.id,
        CASE WHEN joins.value THEN w.head_id ELSE o.id END,
        CASE WHEN joins.value THEN w.group_type ELSE o.movement_type END,
        CASE WHEN joins.value THEN w.group_auto ELSE o.movement_type_source = 'AUTO' END,
        CASE WHEN joins.value THEN w.group_point_count + o.point_count ELSE o.point_count END
    FROM walk w
    JOIN search_path_segment_merge_order o
        ON o.search_path_id = w.search_path_id AND o.position = w.position + 1
    CROSS JOIN LATERAL (
        SELECT w.group_auto AND o.movement_type_source = 'AUTO' AND o.movement_type = w.group_type
            AND w.group_point_count + o.point_count <= 120 AS value
    ) joins
)
SELECT w.id, w.head_id, w.search_path_id
FROM walk w
WHERE w.head_id IN (SELECT head_id FROM walk GROUP BY head_id HAVING count(*) > 1);

-- 다시 만들 구간의 기존 도형과 시각이 원본 GPS와 다르면 범위를 믿을 수 없으므로 중단한다.
INSERT INTO search_path_segment_merge_validation
SELECT 'merged_sources_match_gps', NOT EXISTS (
    SELECT 1
    FROM search_path_segment_merge_members m
    JOIN search_path_segment s ON s.id = m.id
    CROSS JOIN LATERAL (SELECT s.end_point_order - s.start_point_order + 1 AS value) point_count
    WHERE ST_NPoints(s.geometry) <> CASE WHEN point_count.value = 1 THEN 2 ELSE point_count.value END
        -- 좌표 1개짜리 구간의 도형에는 같은 점이 두 번 들어 있어야 한다. 아래 GPS 비교에서는 첫 점만 원본과 대조되므로, 두 번째 점이 첫 점과 같은지 여기서 확인한다.
        OR (point_count.value = 1
            AND (ST_X(ST_PointN(s.geometry, 2)) <> ST_X(ST_PointN(s.geometry, 1))
                OR ST_Y(ST_PointN(s.geometry, 2)) <> ST_Y(ST_PointN(s.geometry, 1))))
        OR s.started_at <> (SELECT client_ts FROM search_path_gps_point
            WHERE search_path_id = s.search_path_id AND point_order = s.start_point_order)
        OR s.ended_at <> (SELECT client_ts FROM search_path_gps_point
            WHERE search_path_id = s.search_path_id AND point_order = s.end_point_order)
        OR EXISTS (
            SELECT 1 FROM search_path_gps_point g
            WHERE g.search_path_id = s.search_path_id
                AND g.point_order BETWEEN s.start_point_order AND s.end_point_order
                AND (g.lon::double precision
                        <> ST_X(ST_PointN(s.geometry, g.point_order - s.start_point_order + 1))
                    OR g.lat::double precision
                        <> ST_Y(ST_PointN(s.geometry, g.point_order - s.start_point_order + 1)))
        )
);

UPDATE search_path p
SET version = p.version + 1,
    updated_at = now()
WHERE p.id IN (SELECT search_path_id FROM search_path_segment_merge_members);

UPDATE search_path_segment s
SET end_point_order = merged.end_order,
    geometry = merged.geometry,
    started_at = merged.started_at,
    ended_at = merged.ended_at,
    version = s.version + 1,
    last_changed_path_version = p.version,
    updated_at = now()
FROM (
    SELECT heads.head_id, heads.search_path_id, heads.end_order,
        ST_SetSRID(ST_MakeLine(array_agg(ST_MakePoint(g.lon::double precision, g.lat::double precision)
            ORDER BY g.point_order)), 4326) AS geometry,
        min(g.client_ts) FILTER (WHERE g.point_order = heads.start_order) AS started_at,
        min(g.client_ts) FILTER (WHERE g.point_order = heads.end_order) AS ended_at
    FROM (
        SELECT m.head_id, m.search_path_id,
            min(s.start_point_order) AS start_order, max(s.end_point_order) AS end_order
        FROM search_path_segment_merge_members m
        JOIN search_path_segment s ON s.id = m.id
        GROUP BY m.head_id, m.search_path_id
    ) heads
    JOIN search_path_gps_point g ON g.search_path_id = heads.search_path_id
        AND g.point_order BETWEEN heads.start_order AND heads.end_order
    GROUP BY heads.head_id, heads.search_path_id, heads.start_order, heads.end_order
) merged
JOIN search_path p ON p.id = merged.search_path_id
WHERE s.id = merged.head_id;

DELETE FROM search_path_segment s
USING search_path_segment_merge_members m
WHERE s.id = m.id AND m.id <> m.head_id;
