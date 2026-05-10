-- S4.json §domain_model.entities[0]: event_dispatch_job outbox 테이블
-- AC-S4-01: domain transaction 안에서 event_dispatch_job row가 원자적으로 보존된다.
-- ux_event_dispatch_job_event_id UNIQUE: S4.json duplicate_event_dedupe fixture
CREATE TABLE event_dispatch_job (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id             UUID         NOT NULL UNIQUE,
    incident_id          UUID         NOT NULL,
    event_type           VARCHAR(64)  NOT NULL,
    payload_format_version INTEGER    NOT NULL DEFAULT 1,
    payload              JSONB        NOT NULL,
    source_entity_type   VARCHAR(64)  NOT NULL,
    source_entity_id     UUID         NOT NULL,
    occurred_at          TIMESTAMPTZ  NOT NULL,
    dispatch_status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_dispatch_job_dispatch
    ON event_dispatch_job (dispatch_status, created_at);

CREATE INDEX idx_event_dispatch_job_incident_occurred
    ON event_dispatch_job (incident_id, occurred_at);
