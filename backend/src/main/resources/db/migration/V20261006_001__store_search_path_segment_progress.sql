-- Expand first: legacy geometry-only inputs remain readable by the existing API.
-- New incremental reads must not be enabled until every target row is backfilled.
ALTER TABLE search_path_segment
    ADD COLUMN start_point_order integer,
    ADD COLUMN end_point_order integer,
    ADD COLUMN last_changed_path_version bigint,
    ADD CONSTRAINT ck_search_path_segment_change_version
        CHECK (last_changed_path_version IS NULL OR last_changed_path_version > 0),
    ADD CONSTRAINT ck_search_path_segment_point_range CHECK (
        (start_point_order IS NULL AND end_point_order IS NULL)
        OR (start_point_order IS NOT NULL AND end_point_order IS NOT NULL
            AND start_point_order >= 0 AND end_point_order >= start_point_order)
    );
