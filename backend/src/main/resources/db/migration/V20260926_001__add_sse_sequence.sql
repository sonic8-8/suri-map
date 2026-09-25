-- 업무 버전과 별개인 사건별 SSE 순번이다. 0은 아직 배정한 순번이 없음을 뜻한다.
ALTER TABLE incident
    ADD COLUMN last_sse_sequence BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_incident_last_sse_sequence_non_negative
        CHECK (last_sse_sequence >= 0);

-- 과거 메모리 순번을 추정하지 않는다. 기존 행은 상태·내용을 보존하고 순번은 비워 둔다.
ALTER TABLE event_dispatch_job
    ADD COLUMN sse_sequence BIGINT,
    ADD CONSTRAINT chk_event_dispatch_job_sse_sequence_positive
        CHECK (sse_sequence > 0);

CREATE UNIQUE INDEX ux_event_dispatch_job_incident_sse_sequence
    ON event_dispatch_job (incident_id, sse_sequence)
    WHERE sse_sequence IS NOT NULL;
