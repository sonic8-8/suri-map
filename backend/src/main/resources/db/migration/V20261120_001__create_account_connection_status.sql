-- 통신 상태를 업무폰이 아니라 계정마다 하나씩 기록한다(#52).
-- 한 계정으로는 동시에 업무폰 한 대에서만 로그인하므로, 계정의 기록이 곧 지금 로그인한 업무폰의 상태다.
-- 업무폰 ID에는 마지막으로 heartbeat를 보낸 업무폰만 남긴다. 이 컬럼은 police_phone을 참조하지 않는다.
CREATE TABLE account_connection_status (
    account_id            UUID         PRIMARY KEY REFERENCES account (id),
    last_heartbeat_at     TIMESTAMPTZ  NOT NULL,
    last_sync_at          TIMESTAMPTZ,
    last_police_phone_id  UUID         NOT NULL,
    version               BIGINT       NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL
);
