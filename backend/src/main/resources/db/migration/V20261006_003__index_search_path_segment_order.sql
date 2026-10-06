-- 이력/변경분의 끝부분 페이지와 서버가 발급한 구간 시작 순번 검증에 사용한다.
-- 구간 범위·기존 행은 변경하지 않는다.
CREATE INDEX idx_search_path_segment_point_order
    ON search_path_segment (search_path_id, start_point_order DESC);
