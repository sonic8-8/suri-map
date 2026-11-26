-- 업무폰 인증은 인프라 계층에서 맡으므로(ADR-0037) 서버에 업무폰마다 두던 기록 행을 지운다(#61).
-- manifest와 map-revisions의 배정 구역은 요청한 계정으로 계산되므로 이 테이블을 읽는 SQL은 없다.
-- 다른 테이블의 police_phone_id 컬럼과 값은 남긴다.
-- police_phone을 참조하는 FK가 남아 있는 DB에서는 그 FK를 먼저 지운다.
-- 경계 알림 이벤트의 jsonb payload에는 업무폰 ID가 값으로 남아 있다. 이 값을 이 테이블과 join하는 SQL은 없다.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'f'
          AND confrelid = 'police_phone'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.table_name, fk.conname);
    END LOOP;
END $$;

DROP TABLE police_phone;
