CREATE TABLE offline_package_manifest (
    id VARCHAR(80) PRIMARY KEY,
    incident_id VARCHAR(80) NOT NULL,
    manifest_version INTEGER NOT NULL,
    operational_period_id VARCHAR(80) NOT NULL,
    overall_search_area_id VARCHAR(80) NOT NULL,
    overall_search_area_version BIGINT NOT NULL,
    manifest_hash CHAR(64) NOT NULL,
    manifest_format_version INTEGER NOT NULL,
    manifest_payload CLOB NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX ux_offline_package_manifest_incident_version
    ON offline_package_manifest (incident_id, manifest_version);

CREATE TABLE offline_package_installation (
    id VARCHAR(80) PRIMARY KEY,
    offline_package_manifest_id VARCHAR(80) NOT NULL,
    police_phone_id VARCHAR(80) NOT NULL,
    last_reported_by_account_id VARCHAR(80),
    status VARCHAR(32) NOT NULL,
    total_item_count INTEGER NOT NULL,
    completed_item_count INTEGER NOT NULL,
    failed_item_count INTEGER NOT NULL,
    failed_item_keys VARCHAR ARRAY,
    last_error_code VARCHAR(80),
    last_reported_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_offline_package_installation_status CHECK (
        status IN ('NOT_STARTED', 'DOWNLOADING', 'PARTIAL', 'READY', 'STALE', 'FAILED', 'PURGED')
    )
);

CREATE UNIQUE INDEX ux_offline_package_installation_manifest_phone
    ON offline_package_installation (offline_package_manifest_id, police_phone_id);

CREATE INDEX idx_offline_package_installation_status
    ON offline_package_installation (status);
