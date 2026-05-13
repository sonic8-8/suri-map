ALTER TABLE incident_assignment
    ALTER COLUMN account_id TYPE UUID
    USING CASE account_id
        WHEN 'acct-precinct-cmd' THEN '11111111-1111-1111-1111-111111110001'::uuid
        WHEN 'acct-precinct-car' THEN '11111111-1111-1111-1111-111111110002'::uuid
        WHEN 'acct-precinct-team' THEN '11111111-1111-1111-1111-111111110003'::uuid
        WHEN 'acct-cmd-alpha' THEN '11111111-1111-1111-1111-111111110004'::uuid
        WHEN 'acct-team-alpha' THEN '11111111-1111-1111-1111-111111110005'::uuid
        WHEN 'acct-support-cmd' THEN '11111111-1111-1111-1111-111111110006'::uuid
        WHEN 'acct-support-car' THEN '11111111-1111-1111-1111-111111110007'::uuid
        WHEN 'acct-support-team' THEN '11111111-1111-1111-1111-111111110008'::uuid
        ELSE account_id::uuid
    END;

ALTER TABLE location_data_access_audit
    ALTER COLUMN account_id TYPE UUID
    USING CASE account_id
        WHEN 'acct-precinct-cmd' THEN '11111111-1111-1111-1111-111111110001'::uuid
        WHEN 'acct-precinct-car' THEN '11111111-1111-1111-1111-111111110002'::uuid
        WHEN 'acct-precinct-team' THEN '11111111-1111-1111-1111-111111110003'::uuid
        WHEN 'acct-cmd-alpha' THEN '11111111-1111-1111-1111-111111110004'::uuid
        WHEN 'acct-team-alpha' THEN '11111111-1111-1111-1111-111111110005'::uuid
        WHEN 'acct-support-cmd' THEN '11111111-1111-1111-1111-111111110006'::uuid
        WHEN 'acct-support-car' THEN '11111111-1111-1111-1111-111111110007'::uuid
        WHEN 'acct-support-team' THEN '11111111-1111-1111-1111-111111110008'::uuid
        ELSE account_id::uuid
    END;

ALTER TABLE offline_package_installation
    ALTER COLUMN last_reported_by_account_id TYPE UUID
    USING CASE last_reported_by_account_id
        WHEN 'acct-precinct-cmd' THEN '11111111-1111-1111-1111-111111110001'::uuid
        WHEN 'acct-precinct-car' THEN '11111111-1111-1111-1111-111111110002'::uuid
        WHEN 'acct-precinct-team' THEN '11111111-1111-1111-1111-111111110003'::uuid
        WHEN 'acct-cmd-alpha' THEN '11111111-1111-1111-1111-111111110004'::uuid
        WHEN 'acct-team-alpha' THEN '11111111-1111-1111-1111-111111110005'::uuid
        WHEN 'acct-support-cmd' THEN '11111111-1111-1111-1111-111111110006'::uuid
        WHEN 'acct-support-car' THEN '11111111-1111-1111-1111-111111110007'::uuid
        WHEN 'acct-support-team' THEN '11111111-1111-1111-1111-111111110008'::uuid
        WHEN 'acct-purge-reporter' THEN '11111111-1111-1111-1111-111111119903'::uuid
        ELSE last_reported_by_account_id::uuid
    END;
