CREATE UNIQUE INDEX IF NOT EXISTS ux_search_path_account_active
    ON search_path (account_id)
    WHERE status IN ('RECORDING', 'PAUSED');
