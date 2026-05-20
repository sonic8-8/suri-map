ALTER TABLE search_path
    ADD COLUMN IF NOT EXISTS account_id UUID;

UPDATE search_path sp
SET account_id = COALESCE(sp.account_id, ia.account_id, ds.started_by_account_id)
FROM duty_shift ds
LEFT JOIN incident_assignment ia
  ON ia.id = ds.incident_assignment_id
WHERE sp.duty_shift_id = ds.id
  AND sp.account_id IS NULL;

ALTER TABLE search_path
    ALTER COLUMN account_id SET NOT NULL;

ALTER TABLE search_path
    DROP CONSTRAINT IF EXISTS fk_search_path_account;

ALTER TABLE search_path
    ADD CONSTRAINT fk_search_path_account
    FOREIGN KEY (account_id) REFERENCES account (id);

CREATE INDEX IF NOT EXISTS idx_search_path_account_status
    ON search_path (account_id, status);
