INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status)
VALUES
    ('11111111-1111-1111-1111-111111110001', 'acct-precinct-cmd', '{noop}fixture', '지구대 지휘관', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110002', 'acct-precinct-car', '{noop}fixture', '지구대 순찰차', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110003', 'acct-precinct-team', '{noop}fixture', '지구대 현장팀', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110004', 'acct-cmd-alpha', '{noop}fixture', '실종팀 지휘관', 'COMMAND', 'MISSING_TEAM', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110005', 'acct-team-alpha', '{noop}fixture', '실종팀 현장팀', 'TEAM', 'MISSING_TEAM', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110006', 'acct-support-cmd', '{noop}fixture', '지원부대 지휘관', 'COMMAND', 'SUPPORT_UNIT', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110007', 'acct-support-car', '{noop}fixture', '지원부대 순찰차', 'PATROL_CAR', 'SUPPORT_UNIT', 'ACTIVE'),
    ('11111111-1111-1111-1111-111111110008', 'acct-support-team', '{noop}fixture', '지원부대 현장팀', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE')
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
    ('00000000-0000-0000-0000-000000000201', 'dev-precinct-cmd-phone-01', '지구대 지휘관 단말', '11111111-1111-1111-1111-111111110001', 'ACTIVE'),
    ('50000000-0000-0000-0000-000000000001', 'dev-precinct-car-01', '지구대 순찰차 단말', '11111111-1111-1111-1111-111111110002', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000101', 'dev-precinct-phone-01', '지구대 현장팀 단말', '11111111-1111-1111-1111-111111110003', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000204', 'dev-alpha-cmd-phone-01', '실종팀 지휘관 단말', '11111111-1111-1111-1111-111111110004', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000205', 'dev-alpha-phone-01', '실종팀 현장팀 단말', '11111111-1111-1111-1111-111111110005', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000206', 'dev-support-cmd-phone-01', '지원부대 지휘관 단말', '11111111-1111-1111-1111-111111110006', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000207', 'dev-support-car-01', '지원부대 순찰차 단말', '11111111-1111-1111-1111-111111110007', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000208', 'dev-support-phone-01', '지원부대 현장팀 단말', '11111111-1111-1111-1111-111111110008', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET
    phone_code = EXCLUDED.phone_code,
    display_name = EXCLUDED.display_name,
    account_id = EXCLUDED.account_id,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
