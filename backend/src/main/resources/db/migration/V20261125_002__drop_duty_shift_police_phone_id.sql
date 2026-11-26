-- 근무는 계정으로 기록하고 표시한다(#58).
-- 근무를 시작한 업무폰 ID는 더 쓰지 않으므로 지운다.
-- 근무한 계정은 incident_assignment_id로 찾는다.
-- idx_duty_shift_phone 인덱스는 컬럼과 함께 지워진다.
-- 근무 시작과 종료 이벤트는 저장되지 않는다.
-- 구역 배정 이벤트 payload에는 업무폰 키가 없으므로 jsonb는 고치지 않는다.
ALTER TABLE duty_shift DROP COLUMN police_phone_id;
