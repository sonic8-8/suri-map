CREATE TABLE IF NOT EXISTS duty_shift (
    id UUID PRIMARY KEY,
    operational_period_id UUID NOT NULL,
    incident_assignment_id UUID NOT NULL,
    police_phone_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    started_by_account_id UUID,
    ended_by_account_id UUID,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_duty_shift_status CHECK (status IN ('ACTIVE', 'ENDED'))
);

CREATE INDEX IF NOT EXISTS idx_duty_shift_op
    ON duty_shift (operational_period_id, status);

CREATE INDEX IF NOT EXISTS idx_duty_shift_phone
    ON duty_shift (police_phone_id, status);

CREATE TABLE IF NOT EXISTS handover_memo (
    id UUID PRIMARY KEY,
    operational_period_id UUID NOT NULL,
    memo_target_type VARCHAR(32) NOT NULL,
    memo_target_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_by_account_id UUID NOT NULL,
    duty_shift_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_handover_memo_target_type CHECK (
        memo_target_type IN (
            'OPERATIONAL_PERIOD',
            'DUTY_SHIFT',
            'SEARCH_PATH',
            'SEARCH_AREA',
            'MARKER'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_handover_memo_op_target
    ON handover_memo (operational_period_id, memo_target_type, memo_target_id);

CREATE TABLE IF NOT EXISTS search_history_summary (
    id UUID PRIMARY KEY,
    operational_period_id UUID NOT NULL,
    duty_shift_id UUID,
    generation_status VARCHAR(24) NOT NULL,
    content TEXT,
    source_data_hash CHAR(64) NOT NULL,
    source_readiness VARCHAR(24) NOT NULL DEFAULT 'READY',
    requested_by_account_id UUID NOT NULL,
    generated_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_search_history_summary_status CHECK (
        generation_status IN ('GENERATING', 'READY', 'FAILED')
    ),
    CONSTRAINT chk_search_history_summary_readiness CHECK (
        source_readiness IN ('PENDING_SYNC', 'READY', 'STALE')
    )
);

CREATE INDEX IF NOT EXISTS idx_search_history_summary_op
    ON search_history_summary (operational_period_id, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_history_summary_scope_hash
    ON search_history_summary (
        operational_period_id,
        COALESCE(duty_shift_id, '00000000-0000-0000-0000-000000000000'::uuid),
        source_data_hash
    );
