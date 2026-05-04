CREATE TABLE IF NOT EXISTS search_area (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    created_op_id UUID NOT NULL,
    parent_area_id UUID, -- split으로 새로 생긴 child만 가지는 값
    split_group_id UUID, -- 같은 split 요청에서 생성된 묶음 ID
    geometry GEOMETRY(POLYGON, 4326) NOT NULL,
    state VARCHAR(32) NOT NULL,
    search_count INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL, -- 해당 row가 수정되면 올라가는 세대 번호
    created_by_account_id UUID NOT NULL,
    client_ts TIMESTAMPTZ NOT NULL,
    server_ts TIMESTAMPTZ NOT NULL,
    clock_offset_ms BIGINT, -- 단말 시계와 서버 시계의 차이를 밀리초로 저장
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_area_state CHECK (
        state IN (
            'ASSIGNED',
            'SEARCHING',
            'FIRST_SEARCH_COMPLETED',
            'RECHECK_REQUIRED',
            'RECHECKING',
            'RECHECK_COMPLETED',
            'ARCHIVED'
        )
    ),
    CONSTRAINT chk_search_area_search_count_non_negative CHECK (search_count >= 0),
    CONSTRAINT chk_search_area_version_positive CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_search_area_incident_state
    ON search_area (incident_id, state);

CREATE INDEX IF NOT EXISTS idx_search_area_created_op
    ON search_area (created_op_id);

CREATE INDEX IF NOT EXISTS idx_search_area_parent
    ON search_area (parent_area_id);

CREATE INDEX IF NOT EXISTS idx_search_area_geom
    ON search_area
    USING GIST (geometry); -- 도형의 범위/겹침 후보를 빠르게 좁히는 인덱스
