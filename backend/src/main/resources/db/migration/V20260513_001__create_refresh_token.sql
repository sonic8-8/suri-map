ALTER TABLE police_phone DROP CONSTRAINT IF EXISTS police_phone_account_id_fkey;
ALTER TABLE police_phone DROP CONSTRAINT IF EXISTS fk_police_phone_account;

UPDATE account
SET id = CASE login_id
    WHEN 'acct-precinct-cmd' THEN '90000000-0000-0000-0000-000000000001'::uuid
    WHEN 'acct-precinct-car' THEN '90000000-0000-0000-0000-000000000002'::uuid
    WHEN 'acct-precinct-team' THEN '90000000-0000-0000-0000-000000000003'::uuid
    WHEN 'acct-cmd-alpha' THEN '90000000-0000-0000-0000-000000000004'::uuid
    WHEN 'acct-team-alpha' THEN '90000000-0000-0000-0000-000000000005'::uuid
    WHEN 'acct-support-cmd' THEN '90000000-0000-0000-0000-000000000006'::uuid
    WHEN 'acct-support-car' THEN '90000000-0000-0000-0000-000000000007'::uuid
    WHEN 'acct-support-team' THEN '90000000-0000-0000-0000-000000000008'::uuid
    ELSE id
END
WHERE login_id IN (
    'acct-precinct-cmd',
    'acct-precinct-car',
    'acct-precinct-team',
    'acct-cmd-alpha',
    'acct-team-alpha',
    'acct-support-cmd',
    'acct-support-car',
    'acct-support-team'
);

UPDATE account
SET id = CASE login_id
    WHEN 'acct-precinct-cmd' THEN '11111111-1111-1111-1111-111111110001'::uuid
    WHEN 'acct-precinct-car' THEN '11111111-1111-1111-1111-111111110002'::uuid
    WHEN 'acct-precinct-team' THEN '11111111-1111-1111-1111-111111110003'::uuid
    WHEN 'acct-cmd-alpha' THEN '11111111-1111-1111-1111-111111110004'::uuid
    WHEN 'acct-team-alpha' THEN '11111111-1111-1111-1111-111111110005'::uuid
    WHEN 'acct-support-cmd' THEN '11111111-1111-1111-1111-111111110006'::uuid
    WHEN 'acct-support-car' THEN '11111111-1111-1111-1111-111111110007'::uuid
    WHEN 'acct-support-team' THEN '11111111-1111-1111-1111-111111110008'::uuid
    ELSE id
END
WHERE login_id IN (
    'acct-precinct-cmd',
    'acct-precinct-car',
    'acct-precinct-team',
    'acct-cmd-alpha',
    'acct-team-alpha',
    'acct-support-cmd',
    'acct-support-car',
    'acct-support-team'
);

UPDATE police_phone
SET
    id = CASE phone_code
        WHEN 'dev-precinct-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000201'::uuid
        WHEN 'dev-precinct-car-01' THEN '50000000-0000-0000-0000-000000000001'::uuid
        WHEN 'dev-precinct-phone-01' THEN '00000000-0000-0000-0000-000000000101'::uuid
        WHEN 'dev-alpha-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000204'::uuid
        WHEN 'dev-alpha-phone-01' THEN '00000000-0000-0000-0000-000000000205'::uuid
        WHEN 'dev-support-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000206'::uuid
        WHEN 'dev-support-car-01' THEN '00000000-0000-0000-0000-000000000207'::uuid
        WHEN 'dev-support-phone-01' THEN '00000000-0000-0000-0000-000000000208'::uuid
        ELSE id
    END,
    account_id = CASE phone_code
        WHEN 'dev-precinct-cmd-phone-01' THEN '11111111-1111-1111-1111-111111110001'::uuid
        WHEN 'dev-precinct-car-01' THEN '11111111-1111-1111-1111-111111110002'::uuid
        WHEN 'dev-precinct-phone-01' THEN '11111111-1111-1111-1111-111111110003'::uuid
        WHEN 'dev-alpha-cmd-phone-01' THEN '11111111-1111-1111-1111-111111110004'::uuid
        WHEN 'dev-alpha-phone-01' THEN '11111111-1111-1111-1111-111111110005'::uuid
        WHEN 'dev-support-cmd-phone-01' THEN '11111111-1111-1111-1111-111111110006'::uuid
        WHEN 'dev-support-car-01' THEN '11111111-1111-1111-1111-111111110007'::uuid
        WHEN 'dev-support-phone-01' THEN '11111111-1111-1111-1111-111111110008'::uuid
        ELSE account_id
    END
WHERE phone_code IN (
    'dev-precinct-cmd-phone-01',
    'dev-precinct-car-01',
    'dev-precinct-phone-01',
    'dev-alpha-cmd-phone-01',
    'dev-alpha-phone-01',
    'dev-support-cmd-phone-01',
    'dev-support-car-01',
    'dev-support-phone-01'
);

ALTER TABLE police_phone
    ADD CONSTRAINT police_phone_account_id_fkey
    FOREIGN KEY (account_id) REFERENCES account(id);

CREATE TABLE IF NOT EXISTS refresh_token (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(id),
    police_phone_id UUID REFERENCES police_phone(id),
    channel VARCHAR(16) NOT NULL,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_account
    ON refresh_token (account_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_police_phone
    ON refresh_token (police_phone_id);

CREATE INDEX IF NOT EXISTS idx_refresh_token_hash
    ON refresh_token (token_hash);
