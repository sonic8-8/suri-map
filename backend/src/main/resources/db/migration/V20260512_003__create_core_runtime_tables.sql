CREATE TABLE IF NOT EXISTS account (
    id UUID PRIMARY KEY,
    login_id VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    account_type VARCHAR(24) NOT NULL,
    organization_type VARCHAR(32) NOT NULL,
    password_hash TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_type CHECK (account_type IN ('TEAM', 'PATROL_CAR', 'COMMAND')),
    CONSTRAINT chk_account_organization_type CHECK (
        organization_type IN ('MISSING_TEAM', 'SUPPORT_UNIT', 'POLICE_SUBSTATION')
    ),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_account_login_id
    ON account (login_id);

CREATE INDEX IF NOT EXISTS idx_account_organization_type
    ON account (organization_type, account_type);

CREATE TABLE IF NOT EXISTS police_phone (
    id UUID PRIMARY KEY,
    phone_code VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    account_id UUID,
    status VARCHAR(24) NOT NULL,
    registered BOOLEAN NOT NULL DEFAULT FALSE,
    last_heartbeat_at TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_police_phone_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_police_phone_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_police_phone_code
    ON police_phone (phone_code);

CREATE INDEX IF NOT EXISTS idx_police_phone_account_status
    ON police_phone (account_id, status);

CREATE TABLE IF NOT EXISTS refresh_token (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    police_phone_id UUID,
    token_hash TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_refresh_token_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_account
    ON refresh_token (account_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_police_phone
    ON refresh_token (police_phone_id);

CREATE TABLE IF NOT EXISTS fcm_token (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    police_phone_id UUID NOT NULL,
    app_instance_id VARCHAR(120) NOT NULL,
    token_hash TEXT NOT NULL,
    token_ciphertext TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT chk_fcm_token_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_fcm_token_version_positive CHECK (version > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_fcm_token_police_phone_instance_active
    ON fcm_token (police_phone_id, app_instance_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_fcm_token_account_status
    ON fcm_token (account_id, status);

CREATE INDEX IF NOT EXISTS idx_fcm_token_hash_status
    ON fcm_token (token_hash, status);

CREATE TABLE IF NOT EXISTS search_area_assignment (
    id UUID PRIMARY KEY,
    search_area_id UUID NOT NULL,
    assigned_account_id UUID NOT NULL,
    assigned_by_account_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    memo TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_area_assignment_status CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_search_area_assignment_active
    ON search_area_assignment (search_area_id, assigned_account_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_search_area_assignment_assigned_account
    ON search_area_assignment (assigned_account_id, status);

CREATE TABLE IF NOT EXISTS search_path (
    id UUID PRIMARY KEY,
    duty_shift_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    geometry GEOMETRY(LINESTRING, 4326),
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_path_status CHECK (status IN ('RECORDING', 'ENDED')),
    CONSTRAINT chk_search_path_version_positive CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_search_path_duty_shift_status
    ON search_path (duty_shift_id, status);

CREATE INDEX IF NOT EXISTS idx_search_path_geom
    ON search_path
    USING GIST (geometry);

CREATE TABLE IF NOT EXISTS search_path_segment (
    id UUID PRIMARY KEY,
    search_path_id UUID NOT NULL,
    movement_type VARCHAR(24) NOT NULL,
    movement_type_source VARCHAR(24) NOT NULL,
    geometry GEOMETRY(LINESTRING, 4326) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL,
    corrected_by_account_id UUID,
    corrected_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_search_path_segment_movement_type CHECK (
        movement_type IN ('VEHICLE', 'FOOT', 'UNKNOWN')
    ),
    CONSTRAINT chk_search_path_segment_movement_type_source CHECK (
        movement_type_source IN ('AUTO', 'MANUAL')
    ),
    CONSTRAINT chk_search_path_segment_version_positive CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_search_path_segment_path
    ON search_path_segment (search_path_id);

CREATE INDEX IF NOT EXISTS idx_search_path_segment_geom
    ON search_path_segment
    USING GIST (geometry);
