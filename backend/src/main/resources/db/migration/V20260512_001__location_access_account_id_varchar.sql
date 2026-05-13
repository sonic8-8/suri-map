ALTER TABLE location_data_access_audit
    ALTER COLUMN account_id TYPE VARCHAR(80) USING account_id::text;
