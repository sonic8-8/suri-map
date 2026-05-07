CREATE TABLE IF NOT EXISTS photo (
    id UUID PRIMARY KEY,
    marker_id UUID NOT NULL,
    object_key TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attached_at TIMESTAMPTZ,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    checksum_sha256 CHAR(64),
    captured_at TIMESTAMPTZ,
    upload_url_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_photo_status CHECK (status IN ('PENDING_UPLOAD', 'ATTACHED', 'FAILED', 'DELETED')),
    CONSTRAINT chk_photo_version_positive CHECK (version > 0),
    CONSTRAINT chk_photo_size_positive CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_photo_marker_status
    ON photo (marker_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS ux_photo_object_key
    ON photo (object_key);
