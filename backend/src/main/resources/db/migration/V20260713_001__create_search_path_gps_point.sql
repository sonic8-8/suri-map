CREATE TABLE search_path_gps_point (
    search_path_id UUID NOT NULL,
    point_order INTEGER NOT NULL,
    point_id VARCHAR(120) NOT NULL,
    client_ts TIMESTAMPTZ NOT NULL,
    lon NUMERIC NOT NULL,
    lat NUMERIC NOT NULL,
    speed_mps NUMERIC NOT NULL,
    horizontal_accuracy_m INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (search_path_id, point_order),
    CONSTRAINT fk_search_path_gps_point_path
        FOREIGN KEY (search_path_id) REFERENCES search_path (id) ON DELETE CASCADE
);
