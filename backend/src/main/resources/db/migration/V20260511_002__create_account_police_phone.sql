CREATE TABLE IF NOT EXISTS account (
    id UUID PRIMARY KEY,
    login_id VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    organization_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_account_type CHECK (account_type IN ('TEAM', 'PATROL_CAR', 'COMMAND')),
    CONSTRAINT chk_account_organization_type CHECK (
        organization_type IN ('MISSING_TEAM', 'SUPPORT_UNIT', 'POLICE_SUBSTATION')
    ),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_account_organization_type
    ON account (organization_type, account_type);

CREATE TABLE IF NOT EXISTS police_phone (
    id UUID PRIMARY KEY,
    phone_code VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    account_id UUID NOT NULL REFERENCES account (id),
    status VARCHAR(32) NOT NULL,
    last_heartbeat_at TIMESTAMPTZ,
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_police_phone_status CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_police_phone_account
    ON police_phone (account_id, status);

INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status)
VALUES
    ('11111111-1111-1111-1111-111111110001', 'acct-precinct-cmd', '{noop}fixture', 'Precinct commander', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110002', 'acct-cmd-alpha', '{noop}fixture', 'Missing team commander', 'COMMAND', 'MISSING_TEAM', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110003', 'acct-support-cmd', '{noop}fixture', 'Support unit commander', 'COMMAND', 'SUPPORT_UNIT', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110004', 'acct-precinct-team', '{noop}fixture', 'Precinct field team', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110005', 'acct-support-team', '{noop}fixture', 'Support field team', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110006', 'acct-support-car', '{noop}fixture', 'Support patrol car', 'PATROL_CAR', 'SUPPORT_UNIT', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110007', 'acct-precinct-car', '{noop}fixture', 'Precinct patrol car', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110008', 'acct-team-alpha', '{noop}fixture', 'Missing field team', 'TEAM', 'MISSING_TEAM', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110009', 'acct-purge-check', '{noop}fixture', 'Purge check account', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    login_id = EXCLUDED.login_id,
    password_hash = EXCLUDED.password_hash,
    display_name = EXCLUDED.display_name,
    account_type = EXCLUDED.account_type,
    organization_type = EXCLUDED.organization_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'incident_assignment'
          AND column_name = 'account_id'
          AND data_type <> 'uuid'
    ) THEN
        ALTER TABLE incident_assignment
            ALTER COLUMN account_id TYPE UUID
            USING (
                CASE account_id
                    WHEN 'acct-precinct-cmd' THEN '11111111-1111-1111-1111-111111110001'
                    WHEN 'acct-cmd-alpha' THEN '11111111-1111-1111-1111-111111110002'
                    WHEN 'acct-support-cmd' THEN '11111111-1111-1111-1111-111111110003'
                    WHEN 'acct-precinct-team' THEN '11111111-1111-1111-1111-111111110004'
                    WHEN 'acct-support-team' THEN '11111111-1111-1111-1111-111111110005'
                    WHEN 'acct-support-car' THEN '11111111-1111-1111-1111-111111110006'
                    WHEN 'acct-precinct-car' THEN '11111111-1111-1111-1111-111111110007'
                    WHEN 'acct-team-alpha' THEN '11111111-1111-1111-1111-111111110008'
                    ELSE account_id
                END
            )::UUID;
    END IF;
END $$;

INSERT INTO police_phone (id, phone_code, display_name, account_id, status)
VALUES
    ('22222222-2222-2222-2222-222222220001', 'dev-precinct-cmd-phone-01', '경찰서 지휘관 폰', '11111111-1111-1111-1111-111111110001', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220002', 'dev-alpha-cmd-phone-01', '실종팀 지휘관 폰', '11111111-1111-1111-1111-111111110002', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220003', 'dev-support-cmd-phone-01', '지원부대 지휘관 폰', '11111111-1111-1111-1111-111111110003', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220004', 'dev-precinct-phone-01', '경찰서 팀폰', '11111111-1111-1111-1111-111111110004', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220005', 'dev-support-phone-01', '지원부대 팀폰', '11111111-1111-1111-1111-111111110005', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220006', 'dev-support-car-01', '지원부대 순찰 폰', '11111111-1111-1111-1111-111111110006', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220007', 'dev-precinct-car-01', '경찰서 순찰 폰', '11111111-1111-1111-1111-111111110007', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220008', 'dev-alpha-phone-01', '실종팀 폰', '11111111-1111-1111-1111-111111110008', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222220009', 'dev-purge-check-phone-01', '파기 확인 폰', '11111111-1111-1111-1111-111111110009', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    phone_code = EXCLUDED.phone_code,
    display_name = EXCLUDED.display_name,
    account_id = EXCLUDED.account_id,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
