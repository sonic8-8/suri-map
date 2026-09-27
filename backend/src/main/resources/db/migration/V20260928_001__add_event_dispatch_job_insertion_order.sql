-- 같은 트랜잭션의 created_at은 같을 수 있으므로 저장 순서를 별도로 남긴다.
-- 기존 행의 순서는 추정하지 않고 NULL로 둔다. 공개 SSE 순번과는 별개다.
ALTER TABLE event_dispatch_job
    ADD COLUMN insertion_order BIGINT,
    ADD CONSTRAINT chk_event_dispatch_job_insertion_order_positive
        CHECK (insertion_order > 0);

CREATE SEQUENCE event_dispatch_job_insertion_order_seq AS BIGINT
    OWNED BY event_dispatch_job.insertion_order;

-- 기본값을 나중에 지정해 기존 행은 보존하고 이후 INSERT에만 번호를 발급한다.
ALTER TABLE event_dispatch_job
    ALTER COLUMN insertion_order
        SET DEFAULT nextval('event_dispatch_job_insertion_order_seq');
