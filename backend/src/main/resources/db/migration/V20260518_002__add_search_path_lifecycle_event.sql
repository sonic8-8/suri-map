ALTER TABLE search_path
    DROP CONSTRAINT IF EXISTS chk_search_path_status;

ALTER TABLE search_path
    ADD CONSTRAINT chk_search_path_status
    CHECK (status IN ('RECORDING', 'PAUSED', 'ENDED'));

CREATE TABLE IF NOT EXISTS search_path_lifecycle_event (
    id UUID PRIMARY KEY,
    search_path_id UUID NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    client_ts TIMESTAMPTZ NOT NULL,
    server_received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_police_phone_id UUID NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_search_path_lifecycle_event_path
        FOREIGN KEY (search_path_id) REFERENCES search_path (id) ON DELETE CASCADE,
    CONSTRAINT chk_search_path_lifecycle_event_type
        CHECK (event_type IN ('STARTED', 'PAUSED', 'RESUMED', 'ENDED')),
    CONSTRAINT chk_search_path_lifecycle_event_version_positive CHECK (version > 0)
);

CREATE INDEX IF NOT EXISTS idx_search_path_lifecycle_event_path_version
    ON search_path_lifecycle_event (search_path_id, version);

CREATE INDEX IF NOT EXISTS idx_search_path_lifecycle_event_actor_phone
    ON search_path_lifecycle_event (actor_police_phone_id, client_ts DESC);

INSERT INTO search_path_lifecycle_event (
    id,
    search_path_id,
    event_type,
    client_ts,
    server_received_at,
    actor_police_phone_id,
    version,
    created_at
)
SELECT
    md5('search-path-lifecycle:' || sp.id::text || ':STARTED:1')::uuid,
    sp.id,
    'STARTED',
    sp.started_at,
    sp.created_at,
    ds.police_phone_id,
    1,
    sp.created_at
FROM search_path sp
JOIN duty_shift ds
  ON ds.id = sp.duty_shift_id
ON CONFLICT (id) DO NOTHING;

INSERT INTO search_path_lifecycle_event (
    id,
    search_path_id,
    event_type,
    client_ts,
    server_received_at,
    actor_police_phone_id,
    version,
    created_at
)
SELECT
    md5('search-path-lifecycle:' || sp.id::text || ':ENDED:' || sp.version::text)::uuid,
    sp.id,
    'ENDED',
    COALESCE(sp.ended_at, sp.updated_at),
    sp.updated_at,
    ds.police_phone_id,
    sp.version,
    sp.updated_at
FROM search_path sp
JOIN duty_shift ds
  ON ds.id = sp.duty_shift_id
WHERE sp.status = 'ENDED'
ON CONFLICT (id) DO NOTHING;
