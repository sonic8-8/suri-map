CREATE TABLE IF NOT EXISTS op_comparison_analysis (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    operational_period_ids JSONB NOT NULL,
    request_hash TEXT NOT NULL,
    source_data_hash TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    metrics_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    diff_facts_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    common_regions_geojson JSONB NOT NULL DEFAULT '[]'::jsonb,
    narrative_status VARCHAR(24) NOT NULL,
    observations_json JSONB,
    failure_reason TEXT,
    requested_by_account_id UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_op_comparison_analysis_status CHECK (
        status IN ('GENERATING', 'READY', 'FAILED')
    ),
    CONSTRAINT chk_op_comparison_analysis_narrative_status CHECK (
        narrative_status IN ('SKIPPED', 'GENERATING', 'READY', 'FAILED')
    ),
    CONSTRAINT chk_op_comparison_analysis_op_ids_array CHECK (
        jsonb_typeof(operational_period_ids) = 'array'
    ),
    CONSTRAINT chk_op_comparison_analysis_source_hash CHECK (
        LENGTH(source_data_hash) = 64
    ),
    CONSTRAINT chk_op_comparison_analysis_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_op_comparison_analysis_request_hash
    ON op_comparison_analysis (request_hash);

CREATE INDEX IF NOT EXISTS idx_op_comparison_analysis_incident_requested
    ON op_comparison_analysis (incident_id, requested_at DESC, id);

CREATE INDEX IF NOT EXISTS idx_op_comparison_analysis_status
    ON op_comparison_analysis (status, narrative_status, updated_at);
