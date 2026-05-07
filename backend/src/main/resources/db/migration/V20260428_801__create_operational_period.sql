-- S8 operational_period 테이블 생성 (S8.json §domain_model.flyway_migrations)
CREATE TABLE IF NOT EXISTS operational_period (
    id                    UUID         NOT NULL,
    incident_id           UUID         NOT NULL,
    sequence_number       INTEGER      NOT NULL,
    status                VARCHAR(24)  NOT NULL,
    reason                VARCHAR(32)  NOT NULL,
    reason_memo           TEXT,
    started_by_account_id UUID,
    ended_by_account_id   UUID,
    started_at            TIMESTAMPTZ  NOT NULL,
    ended_at              TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_operational_period PRIMARY KEY (id)
);

-- incident 내 sequence_number 유니크 보장
CREATE UNIQUE INDEX IF NOT EXISTS ux_operational_period_incident_sequence
    ON operational_period (incident_id, sequence_number);

-- incident당 ACTIVE OP는 1개만 허용
CREATE UNIQUE INDEX IF NOT EXISTS ux_operational_period_one_active
    ON operational_period (incident_id)
    WHERE status = 'ACTIVE';
