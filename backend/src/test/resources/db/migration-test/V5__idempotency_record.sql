CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY,
    client_operation_id VARCHAR(120),
    incident_id UUID,
    police_phone_id UUID,
    idempotency_key VARCHAR(160) NOT NULL,
    request_body_hash CHAR(64) NOT NULL,
    request_path VARCHAR(200) NOT NULL,
    request_method VARCHAR(16) NOT NULL,
    idempotency_status VARCHAR(32) NOT NULL,
    response_status_code INTEGER,
    response_body_json CLOB,
    response_content_type VARCHAR(80),
    response_body_format_version INTEGER,
    result_entity_type VARCHAR(80),
    result_entity_id UUID,
    result_entity_status VARCHAR(32),
    result_entity_version BIGINT,
    result_entity_sequence BIGINT,
    replay_recovered BOOLEAN NOT NULL DEFAULT FALSE,
    client_requested_at TIMESTAMP WITH TIME ZONE,
    clock_offset_ms BIGINT,
    replay_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_idempotency_status CHECK (
        idempotency_status IN ('RESERVED', 'COMPLETED')
    )
);

CREATE UNIQUE INDEX ux_idempotency_record_key_path_method
    ON idempotency_record (idempotency_key, request_path, request_method);
