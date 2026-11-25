-- 마커 수정 권한과 마커 알림 수신 업무폰은 계정으로 정한다(#57).
-- 마커를 남긴 업무폰 ID와 알림을 만들 때 정한 수신 업무폰 목록은 더 쓰지 않으므로 지운다.
-- 마커 작성 계정은 created_by_account_id에, 알림 수신 계정은 recipient_account_ids에 남아 있다.
-- idx_marker_police_phone 인덱스는 컬럼과 함께 지워진다.
ALTER TABLE marker DROP COLUMN police_phone_id;

ALTER TABLE marker_notification DROP COLUMN recipient_police_phone_ids;

-- 배포 전에 저장한 마커 이벤트와 알림 snapshot에서도 업무폰 키를 지운다.
-- 저장된 SSE payload는 재전송할 때 그대로 나가기 때문이다.
-- 배정 변경과 사건 종료 이벤트의 recipientPolicePhoneIds는 그대로 둔다.
UPDATE event_dispatch_job
SET payload = payload - 'policePhoneId' - 'policePhoneName' - 'recipientPolicePhoneIds'
WHERE event_type IN (
        'MARKER_CREATED', 'MARKER_UPDATED', 'MARKER_DELETED',
        'SUPPORT_REQUEST_CREATED', 'PERSON_FOUND'
    )
  AND jsonb_exists_any(
        payload, ARRAY['policePhoneId', 'policePhoneName', 'recipientPolicePhoneIds']);

UPDATE marker_notification
SET notification_payload =
        notification_payload - 'policePhoneId' - 'policePhoneName' - 'recipientPolicePhoneIds'
WHERE jsonb_exists_any(
        notification_payload,
        ARRAY['policePhoneId', 'policePhoneName', 'recipientPolicePhoneIds']);

-- 배포 전에 저장한 마커 알림에는 작성 계정 이름이 없다.
-- 이 이름이 없으면 재전송한 알림의 작성자가 "작성자 확인 불가"로 나온다.
-- 마커의 작성 계정으로 이름을 채운다. 이름을 찾지 못한 행은 그대로 둔다.
UPDATE event_dispatch_job job
SET payload = job.payload || jsonb_build_object('createdByAccountDisplayName', a.display_name)
FROM marker m
JOIN account a ON a.id = m.created_by_account_id
WHERE job.event_type IN ('SUPPORT_REQUEST_CREATED', 'PERSON_FOUND')
  AND NOT jsonb_exists(job.payload, 'createdByAccountDisplayName')
  AND m.id::text = job.payload ->> 'markerId'
  AND btrim(a.display_name) <> '';

UPDATE marker_notification mn
SET notification_payload =
        mn.notification_payload
        || jsonb_build_object('createdByAccountDisplayName', a.display_name)
FROM marker m
JOIN account a ON a.id = m.created_by_account_id
WHERE m.id = mn.marker_id
  AND NOT jsonb_exists(mn.notification_payload, 'createdByAccountDisplayName')
  AND btrim(a.display_name) <> '';
