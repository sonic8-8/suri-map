ALTER TABLE idempotency_record
    ADD COLUMN IF NOT EXISTS response_content_type VARCHAR(80);

ALTER TABLE idempotency_record
    ADD COLUMN IF NOT EXISTS result_entity_sequence BIGINT;

ALTER TABLE idempotency_record
    ADD COLUMN IF NOT EXISTS replay_recovered BOOLEAN NOT NULL DEFAULT FALSE;
