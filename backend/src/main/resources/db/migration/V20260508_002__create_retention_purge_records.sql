-- S1-3 internal/system-only retention and purge records.
CREATE TABLE incident_data_purge (
    id                 UUID         PRIMARY KEY,
    incident_id        UUID         NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    closed_at          TIMESTAMPTZ  NOT NULL,
    purge_due_at       TIMESTAMPTZ  NOT NULL,
    completed_at       TIMESTAMPTZ,
    last_error_code    VARCHAR(80),
    version            BIGINT       NOT NULL DEFAULT 1,
    environment_policy VARCHAR(40)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_incident_data_purge_incident UNIQUE (incident_id)
);

CREATE INDEX idx_incident_data_purge_status_due
    ON incident_data_purge (status, purge_due_at);

CREATE TABLE incident_data_purge_hook_step (
    purge_run_id   UUID         NOT NULL,
    hook_name      VARCHAR(40)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    purged_count   BIGINT       NOT NULL DEFAULT 0,
    retained_count BIGINT       NOT NULL DEFAULT 0,
    error_code     VARCHAR(80),
    completed_at   TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (purge_run_id, hook_name),
    CONSTRAINT fk_purge_hook_step_run
        FOREIGN KEY (purge_run_id) REFERENCES incident_data_purge (id)
);

CREATE TABLE location_data_access_audit (
    id              UUID         PRIMARY KEY,
    incident_id     UUID         NOT NULL,
    account_id      UUID         NOT NULL,
    police_phone_id UUID,
    access_channel  VARCHAR(16)  NOT NULL,
    access_purpose  VARCHAR(32)  NOT NULL,
    accessed_at     TIMESTAMPTZ  NOT NULL,
    retention_until TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_location_data_access_audit_incident_time
    ON location_data_access_audit (incident_id, accessed_at);

CREATE INDEX idx_location_data_access_audit_retention
    ON location_data_access_audit (retention_until);
