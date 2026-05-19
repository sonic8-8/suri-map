CREATE TABLE IF NOT EXISTS search_area_boundary_alert (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    operational_period_id UUID NOT NULL,
    search_area_id UUID NOT NULL,
    police_phone_id UUID NOT NULL,
    search_path_id UUID,
    alert_type VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    location GEOMETRY(Point, 4326) NOT NULL,
    client_ts TIMESTAMPTZ NOT NULL,
    server_received_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_area_boundary_alert_type
        CHECK (alert_type IN ('OUTSIDE_ASSIGNED_AREA', 'REENTERED_ASSIGNED_AREA')),
    CONSTRAINT chk_search_area_boundary_alert_status
        CHECK (status IN ('RECORDED')),
    CONSTRAINT chk_search_area_boundary_alert_version_positive
        CHECK (version > 0),
    CONSTRAINT fk_search_area_boundary_alert_incident
        FOREIGN KEY (incident_id) REFERENCES incident(id),
    CONSTRAINT fk_search_area_boundary_alert_op
        FOREIGN KEY (operational_period_id) REFERENCES operational_period(id),
    CONSTRAINT fk_search_area_boundary_alert_area
        FOREIGN KEY (search_area_id) REFERENCES search_area(id),
    CONSTRAINT fk_search_area_boundary_alert_phone
        FOREIGN KEY (police_phone_id) REFERENCES police_phone(id),
    CONSTRAINT fk_search_area_boundary_alert_path
        FOREIGN KEY (search_path_id) REFERENCES search_path(id)
);

CREATE INDEX IF NOT EXISTS idx_search_area_boundary_alert_incident_op
    ON search_area_boundary_alert (incident_id, operational_period_id, client_ts DESC);

CREATE INDEX IF NOT EXISTS idx_search_area_boundary_alert_phone
    ON search_area_boundary_alert (police_phone_id, client_ts DESC);

CREATE INDEX IF NOT EXISTS idx_search_area_boundary_alert_area
    ON search_area_boundary_alert (search_area_id, client_ts DESC);
