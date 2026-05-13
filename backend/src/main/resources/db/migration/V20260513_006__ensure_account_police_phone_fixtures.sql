INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status)
VALUES
    ('11111111-1111-1111-1111-111111110001', 'acct-precinct-cmd', '{noop}fixture', 'Precinct commander', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110002', 'acct-precinct-car', '{noop}fixture', 'Precinct patrol car', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110003', 'acct-precinct-team', '{noop}fixture', 'Precinct field team', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110004', 'acct-cmd-alpha', '{noop}fixture', 'Missing team commander', 'COMMAND', 'MISSING_TEAM', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110005', 'acct-team-alpha', '{noop}fixture', 'Missing field team', 'TEAM', 'MISSING_TEAM', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110006', 'acct-support-cmd', '{noop}fixture', 'Support unit commander', 'COMMAND', 'SUPPORT_UNIT', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110007', 'acct-support-car', '{noop}fixture', 'Support patrol car', 'PATROL_CAR', 'SUPPORT_UNIT', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110008', 'acct-support-team', '{noop}fixture', 'Support field team', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    login_id = EXCLUDED.login_id,
    password_hash = EXCLUDED.password_hash,
    display_name = EXCLUDED.display_name,
    account_type = EXCLUDED.account_type,
    organization_type = EXCLUDED.organization_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO police_phone (id, phone_code, display_name, account_id, status)
VALUES
    ('00000000-0000-0000-0000-000000000201', 'dev-precinct-cmd-phone-01', 'Precinct commander phone', '11111111-1111-1111-1111-111111110001', 'ACTIVE'),
    ('50000000-0000-0000-0000-000000000001', 'dev-precinct-car-01', 'Precinct patrol phone', '11111111-1111-1111-1111-111111110002', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000101', 'dev-precinct-phone-01', 'Precinct team phone', '11111111-1111-1111-1111-111111110003', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000204', 'dev-alpha-cmd-phone-01', 'Missing commander phone', '11111111-1111-1111-1111-111111110004', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000205', 'dev-alpha-phone-01', 'Missing team phone', '11111111-1111-1111-1111-111111110005', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000206', 'dev-support-cmd-phone-01', 'Support commander phone', '11111111-1111-1111-1111-111111110006', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000207', 'dev-support-car-01', 'Support patrol phone', '11111111-1111-1111-1111-111111110007', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000208', 'dev-support-phone-01', 'Support team phone', '11111111-1111-1111-1111-111111110008', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    phone_code = EXCLUDED.phone_code,
    display_name = EXCLUDED.display_name,
    account_id = EXCLUDED.account_id,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
