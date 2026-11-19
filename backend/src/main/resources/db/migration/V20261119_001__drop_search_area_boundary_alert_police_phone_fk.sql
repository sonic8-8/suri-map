-- 업무폰 인증은 인프라 계층이 맡으므로(ADR-0037) 경계 알림에는 요청한 업무폰 ID를 기록용으로만 남긴다.
-- police_phone에 없는 업무폰 ID도 저장되도록 FK만 없앤다. 컬럼과 값은 그대로 둔다.
ALTER TABLE search_area_boundary_alert
    DROP CONSTRAINT IF EXISTS fk_search_area_boundary_alert_phone;
