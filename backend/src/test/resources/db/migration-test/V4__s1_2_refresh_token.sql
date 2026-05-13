UPDATE police_phone
SET id = CASE phone_code
  WHEN 'dev-precinct-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000201'
  WHEN 'dev-precinct-car-01' THEN '50000000-0000-0000-0000-000000000001'
  WHEN 'dev-precinct-phone-01' THEN '00000000-0000-0000-0000-000000000101'
  WHEN 'dev-alpha-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000204'
  WHEN 'dev-alpha-phone-01' THEN '00000000-0000-0000-0000-000000000205'
  WHEN 'dev-support-cmd-phone-01' THEN '00000000-0000-0000-0000-000000000206'
  WHEN 'dev-support-car-01' THEN '00000000-0000-0000-0000-000000000207'
  WHEN 'dev-support-phone-01' THEN '00000000-0000-0000-0000-000000000208'
  ELSE id
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

CREATE TABLE refresh_token (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES account(id),
  police_phone_id UUID,
  channel VARCHAR(16) NOT NULL,
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  revoked_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
