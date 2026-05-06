CREATE TABLE IF NOT EXISTS search_area (
    id UUID PRIMARY KEY,
    operational_period_id UUID NOT NULL,
    parent_search_area_id UUID,
    name VARCHAR(120) NOT NULL,
    area_level VARCHAR(24) NOT NULL,
    geometry GEOMETRY(POLYGON, 4326) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    created_by_account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_area_area_level CHECK (area_level IN ('OVERALL', 'UNIT', 'TEAM')),
    CONSTRAINT chk_search_area_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_search_area_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_area_one_active_overall_per_op
    ON search_area (operational_period_id)
    WHERE area_level = 'OVERALL' AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_search_area_op_status
    ON search_area (operational_period_id, status);

CREATE INDEX IF NOT EXISTS idx_search_area_parent
    ON search_area (parent_search_area_id);

CREATE INDEX IF NOT EXISTS idx_search_area_geom
    ON search_area
    USING GIST (geometry);
