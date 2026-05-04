CREATE TABLE IF NOT EXISTS map_boundary (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    geometry GEOMETRY(POLYGON, 4326) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    created_by_account_id UUID NOT NULL,
    client_ts TIMESTAMPTZ NOT NULL,
    server_ts TIMESTAMPTZ NOT NULL,
    clock_offset_ms BIGINT, -- 단말 시계와 서버 시계의 차이를 밀리초로 저장
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_map_boundary_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_map_boundary_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_map_boundary_one_active_per_incident
    ON map_boundary (incident_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_map_boundary_geom
    ON map_boundary
    USING GIST (geometry);
