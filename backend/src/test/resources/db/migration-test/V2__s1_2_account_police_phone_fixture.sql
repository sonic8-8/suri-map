CREATE TABLE account (
  id VARCHAR(64) PRIMARY KEY,
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
  id VARCHAR(64) PRIMARY KEY,
  phone_code VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(128) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_heartbeat_at TIMESTAMP WITH TIME ZONE,
  last_sync_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_police_phone_account FOREIGN KEY (account_id) REFERENCES account(id)
);

INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status)
VALUES
  ('acct-precinct-cmd', 'acct-precinct-cmd', '{noop}fixture', '종로 지구대 지휘', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE'),
  ('acct-precinct-car', 'acct-precinct-car', '{noop}fixture', '종로 지구대 순찰차', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE'),
  ('acct-precinct-team', 'acct-precinct-team', '{noop}fixture', '종로 지구대 팀', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE'),
  ('acct-cmd-alpha', 'acct-cmd-alpha', '{noop}fixture', '실종팀 알파 지휘', 'COMMAND', 'MISSING_TEAM', 'ACTIVE'),
  ('acct-team-alpha', 'acct-team-alpha', '{noop}fixture', '실종팀 알파 팀', 'TEAM', 'MISSING_TEAM', 'ACTIVE'),
  ('acct-support-cmd', 'acct-support-cmd', '{noop}fixture', '지원 브라보 지휘', 'COMMAND', 'SUPPORT_UNIT', 'ACTIVE'),
  ('acct-support-car', 'acct-support-car', '{noop}fixture', '지원 브라보 순찰차', 'PATROL_CAR', 'SUPPORT_UNIT', 'ACTIVE'),
  ('acct-support-team', 'acct-support-team', '{noop}fixture', '지원 브라보 팀', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE');

INSERT INTO police_phone (id, phone_code, display_name, account_id, status)
VALUES
  ('dev-precinct-cmd-phone-01', 'dev-precinct-cmd-phone-01', '종로 지구대 지휘 폴리폰', 'acct-precinct-cmd', 'ACTIVE'),
  ('dev-precinct-car-01', 'dev-precinct-car-01', '종로 지구대 순찰차 폴리폰', 'acct-precinct-car', 'ACTIVE'),
  ('dev-precinct-phone-01', 'dev-precinct-phone-01', '종로 지구대 팀 폴리폰', 'acct-precinct-team', 'ACTIVE'),
  ('dev-alpha-cmd-phone-01', 'dev-alpha-cmd-phone-01', '실종팀 알파 지휘 폴리폰', 'acct-cmd-alpha', 'ACTIVE'),
  ('dev-alpha-phone-01', 'dev-alpha-phone-01', '실종팀 알파 폴리폰', 'acct-team-alpha', 'ACTIVE'),
  ('dev-support-cmd-phone-01', 'dev-support-cmd-phone-01', '지원 브라보 지휘 폴리폰', 'acct-support-cmd', 'ACTIVE'),
  ('dev-support-car-01', 'dev-support-car-01', '지원 브라보 순찰차 폴리폰', 'acct-support-car', 'ACTIVE'),
  ('dev-support-phone-01', 'dev-support-phone-01', '지원 브라보 팀 폴리폰', 'acct-support-team', 'ACTIVE');
