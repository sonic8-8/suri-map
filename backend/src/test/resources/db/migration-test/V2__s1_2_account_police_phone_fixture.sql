CREATE TABLE account (
  id UUID PRIMARY KEY,
  login_id VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  organization_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE police_phone (
  id UUID PRIMARY KEY,
  phone_code VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(128) NOT NULL,
  account_id UUID NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_heartbeat_at TIMESTAMP WITH TIME ZONE,
  last_sync_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_police_phone_account FOREIGN KEY (account_id) REFERENCES account(id)
);

INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status)
VALUES
  ('11111111-1111-1111-1111-111111110001', 'acct-precinct-cmd', '{noop}fixture', '종로 지구대 지휘', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111110002', 'acct-precinct-car', '{noop}fixture', '종로 지구대 순찰차', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111110003', 'acct-precinct-team', '{noop}fixture', '종로 지구대 팀', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111110004', 'acct-cmd-alpha', '{noop}fixture', '실종팀 알파 지휘', 'COMMAND', 'MISSING_TEAM', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111110005', 'acct-team-alpha', '{noop}fixture', '실종팀 알파 팀', 'TEAM', 'MISSING_TEAM', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111110006', 'acct-support-cmd', '{noop}fixture', '지원 브라보 지휘', 'COMMAND', 'SUPPORT_UNIT', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111110007', 'acct-support-car', '{noop}fixture', '지원 브라보 순찰차', 'PATROL_CAR', 'SUPPORT_UNIT', 'ACTIVE'),
  ('11111111-1111-1111-1111-111111110008', 'acct-support-team', '{noop}fixture', '지원 브라보 팀', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE');

INSERT INTO police_phone (id, phone_code, display_name, account_id, status)
VALUES
  ('00000000-0000-0000-0000-000000000201', 'dev-precinct-cmd-phone-01', '종로 지구대 지휘 폴리폰', '11111111-1111-1111-1111-111111110001', 'ACTIVE'),
  ('50000000-0000-0000-0000-000000000001', 'dev-precinct-car-01', '종로 지구대 순찰차 폴리폰', '11111111-1111-1111-1111-111111110002', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000101', 'dev-precinct-phone-01', '종로 지구대 팀 폴리폰', '11111111-1111-1111-1111-111111110003', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000204', 'dev-alpha-cmd-phone-01', '실종팀 알파 지휘 폴리폰', '11111111-1111-1111-1111-111111110004', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000205', 'dev-alpha-phone-01', '실종팀 알파 폴리폰', '11111111-1111-1111-1111-111111110005', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000206', 'dev-support-cmd-phone-01', '지원 브라보 지휘 폴리폰', '11111111-1111-1111-1111-111111110006', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000207', 'dev-support-car-01', '지원 브라보 순찰차 폴리폰', '11111111-1111-1111-1111-111111110007', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000208', 'dev-support-phone-01', '지원 브라보 팀 폴리폰', '11111111-1111-1111-1111-111111110008', 'ACTIVE');
