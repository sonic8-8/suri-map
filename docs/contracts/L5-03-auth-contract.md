# L5-03 · 인증·이벤트 소비 계약

상태: 초안. 담당: L5. 소비 대상: S1-2 (Account, PolicePhone & RBAC), S4 (Realtime Event Hub).

## 1. 목적

L5 (Marker / Photo / Notification) 구현 시 L2/L3 Lane이 제공하는 인증·권한·이벤트 계약을 정리한다.
이 문서는 L5가 **소비만 하는** 계약이며, S1-2/S4 소유 구현을 L5 범위로 가져오지 않는다.

## 2. SecurityContext

L5 API는 S1-2가 제공하는 `SecurityContext`를 전제로 한다.

```json
{
  "accountId": "uuid",
  "accountType": "TEAM | PATROL_CAR | COMMAND",
  "affiliation": "MISSING_TEAM | SUPPORT_UNIT | LOCAL_POLICE",
  "roles": ["MISSING_TEAM_COMMANDER", "FIELD_COMMANDER", "MEMBER"],
  "channel": "APP | WEB | INTERNAL",
  "policePhoneId": "uuid | null"
}
```

## 3. L5 Guard 조합

| Annotation | 실패 코드 | L5 사용처 |
|---|---|---|
| `@RequireChannel(APP)` | `channel_not_allowed` | `POST /api/markers`, photo upload-url/attach |
| `@RequirePolicePhone` | `police_phone_required` | marker/photo app write |
| `@RequirePolicePhoneRegistered` | `police_phone_not_registered` | marker/photo app write |
| `@RequirePolicePhoneAssigned` | `police_phone_not_assigned` | marker/photo app write |
| `@RequireIncidentAccess` | `incident_access_denied`, `team_not_assigned` | 모든 L5 API |
| `@RequireCurrentOp` | `op_required`, `op_mismatch` | marker 생성 시 OP 연결 |
| `@IdempotentWrite` | `idempotency_mismatch` | domain write, photo attach replay |

```text
POST /api/markers
  = @RequireChannel(APP)
  + @RequirePolicePhone
  + @RequirePolicePhoneRegistered
  + @RequirePolicePhoneAssigned
  + @RequireIncidentAccess
  + @RequireCurrentOp
  + @IdempotentWrite

POST /api/markers/{markerId}/photos/upload-url
POST /api/markers/{markerId}/photos/{photoId}/attach
  = @RequireChannel(APP)
  + @RequirePolicePhone
  + @RequirePolicePhoneRegistered
  + @RequirePolicePhoneAssigned
  + @RequireIncidentAccess
  + @IdempotentWrite
```

## 4. Account / PolicePhone / FCM Fixture

### 4.1 Account Fixture (S1-2 소유)

| Fixture Alias | accountId | accountType | affiliation | roles |
|---|---|---|---|---|
| 지구대 지휘 | `acct-precinct-cmd` | COMMAND | LOCAL_POLICE | FIELD_COMMANDER |
| 지구대 팀 | `acct-precinct-team` | TEAM | LOCAL_POLICE | MEMBER |
| 지구대 순찰차 | `acct-precinct-car` | PATROL_CAR | LOCAL_POLICE | MEMBER |
| 실종팀 지휘 | `acct-cmd-alpha` | COMMAND | MISSING_TEAM | MISSING_TEAM_COMMANDER |
| 실종팀 팀원 | `acct-team-alpha` | TEAM | MISSING_TEAM | MEMBER |
| 지원 부대 지휘 | `acct-support-cmd` | COMMAND | SUPPORT_UNIT | FIELD_COMMANDER |
| 지원 부대 팀 | `acct-support-team` | TEAM | SUPPORT_UNIT | MEMBER |
| 지원 부대 순찰차 | `acct-support-car` | PATROL_CAR | SUPPORT_UNIT | MEMBER |

### 4.2 PolicePhone Fixture (S1-2 소유)

`dev-*` 값은 하네스의 `policePhoneId` fixture ID다. `COMMANDER`, `TEAM`, `PATROL`은 fixture alias일 뿐 public enum으로 추가하지 않는다.

| policePhoneId | accountId | FCM 수신 대상 | 비고 |
|---|---|---|---|
| `dev-precinct-cmd-phone-01` | `acct-precinct-cmd` | 아니오 | 웹 지휘·incident_assignment 식별자 |
| `dev-precinct-phone-01` | `acct-precinct-team` | 예 | 초동 팀 PolicePhone |
| `dev-precinct-car-01` | `acct-precinct-car` | 예 | 초동 순찰차 PolicePhone |
| `dev-alpha-cmd-phone-01` | `acct-cmd-alpha` | 아니오 | 웹 지휘·incident_assignment 식별자 |
| `dev-alpha-phone-01` | `acct-team-alpha` | 예 | 실종팀 PolicePhone |
| `dev-support-cmd-phone-01` | `acct-support-cmd` | 아니오 | 웹 지휘·incident_assignment 식별자 |
| `dev-support-phone-01` | `acct-support-team` | 예 | 지원 부대 팀 PolicePhone |
| `dev-support-car-01` | `acct-support-car` | 예 | 지원 부대 순찰차 PolicePhone |

### 4.3 FCM Token Query (S1-2 소유, L5 소비)

L5 `NotificationRecipientResolver`는 recipient account/policePhone 목록을 S5 정책으로 계산한 뒤, S1-2가 제공하는 token 조회 계약만 소비한다.

```java
public interface FcmTokenQuery {
    Optional<FcmTokenRef> activeByPolicePhone(String policePhoneId);
}
```

```json
{
  "id": "uuid",
  "policePhoneId": "dev-alpha-phone-01",
  "appInstanceId": "string",
  "tokenCiphertext": "encrypted-token",
  "tokenHash": "log-safe-hash",
  "status": "ACTIVE",
  "version": 1
}
```

### 4.4 FCM Recipient Fixture (S5 소유 정책, S1-2 token 소비)

| 시나리오 | recipientPolicy | recipientAccountIds | recipientPolicePhoneIds | expectedFcmRecipients | 제외 |
|---|---|---|---|---|---|
| 지원 부대 배정 알림 (SC-02) | `NEWLY_ASSIGNED_SUPPORT_DEVICES` | `acct-support-car`, `acct-support-team` | `dev-support-car-01`, `dev-support-phone-01` | `fcm:dev-support-car-01`, `fcm:dev-support-phone-01` | `dev-support-cmd-phone-01` |
| 지원 요청 알림 (SC-08) | `COMMANDERS_AND_FIELD_COMMANDERS` | `acct-cmd-alpha`, `acct-support-cmd`, `acct-support-car`, `acct-support-team` | `dev-alpha-phone-01`, `dev-support-car-01`, `dev-support-phone-01` | `fcm:dev-alpha-phone-01`, `fcm:dev-support-car-01`, `fcm:dev-support-phone-01` | command account policePhone |
| 실종자 발견 알림 (SC-08) | `ALL_INCIDENT_ASSIGNED` | `acct-precinct-cmd`, `acct-precinct-car`, `acct-precinct-team`, `acct-cmd-alpha`, `acct-team-alpha`, `acct-support-cmd`, `acct-support-car`, `acct-support-team` | `dev-precinct-car-01`, `dev-precinct-phone-01`, `dev-alpha-phone-01`, `dev-support-car-01`, `dev-support-phone-01` | `fcm:dev-alpha-phone-01`, `fcm:dev-support-car-01`, `fcm:dev-support-phone-01` | command account policePhone |

## 5. Event Envelope 계약 (S4 소유)

S4가 정의하는 공통 이벤트 포맷이다. L5는 `payload` 필드와 PublishRequest 생성만 소유하고, fanout/retry/SSE replay는 S4가 소유한다.

```json
{
  "eventId": "uuid",
  "incidentId": "uuid",
  "type": "MARKER_CREATED | MARKER_UPDATED | MARKER_DELETED | SUPPORT_REQUEST_CREATED | PERSON_FOUND",
  "schemaVersion": 1,
  "serverTs": "2026-04-28T09:00:00+09:00",
  "payload": {}
}
```

| Event Type | Trigger | Payload 비교 필드 |
|---|---|---|
| `MARKER_CREATED` | `POST /api/markers` 성공 | `id`, `status`, `version`, `opId`, `policePhoneId` |
| `MARKER_UPDATED` | `PATCH /api/markers/{markerId}` 또는 `POST /api/markers/{markerId}/photos/{photoId}/attach` 성공 | `id`, `status`, `version`, `opId`, `policePhoneId`, optional `photoDelta.photoId/status/version` |
| `MARKER_DELETED` | `DELETE /api/markers/{markerId}` 성공 | `id`, `status`, `version`, `opId`, `policePhoneId` |
| `SUPPORT_REQUEST_CREATED` | SUPPORT_REQUEST marker 생성 후 recipient/payload 계산 완료 | `id`, `status`, `version`, `opId`, `policePhoneId`, `recipientPolicy` |
| `PERSON_FOUND` | PERSON_FOUND marker 생성 후 recipient/payload 계산 완료 | `id`, `status`, `version`, `opId`, `policePhoneId`, `recipientPolicy` |

Photo attach는 별도 photo event를 만들지 않는다. attach 성공 시 parent marker version이 증가하고 기존 `MARKER_UPDATED` payload의 `photoDelta.photoId/status/version`으로 REST response, event_dispatch_job/SSE, board marker slot이 수렴해야 한다.

## 6. 필수 헤더 규칙

| 헤더 | 용도 | L5 write API 필수 여부 |
|---|---|---|
| `Authorization` | Bearer token | 예 |
| `X-Client-Channel` | APP / WEB 채널 식별 | 예 |
| `X-PolicePhone-Id` | APP PolicePhone 식별 | APP write에서 예 |
| `Idempotency-Key` | 중복 쓰기 방지 | domain write와 photo attach에서 예 |

## 7. 에러 코드 매트릭스

| HTTP | Error Code | 원인 |
|---|---|---|
| 400 | `invalid_geometry` | marker/photo 위치 geometry 실패 |
| 400 | `police_phone_required` | APP 요청에 PolicePhone 식별자 누락 |
| 403 | `channel_not_allowed` | WEB에서 앱 전용 API 호출 |
| 403 | `police_phone_not_registered` | 미등록 PolicePhone |
| 403 | `police_phone_not_assigned` | 해당 사건/OP 미배정 PolicePhone |
| 403 | `incident_access_denied` | 사건 접근 권한 없음 |
| 403 | `team_not_assigned` | 팀 미배정 |
| 409 | `incident_closed` | 종료된 사건 write 시도 |
| 409 | `idempotency_mismatch` | 다른 body로 같은 key 재사용 |
| 409 | `write_conflict` | version 충돌 |
| 409 | `op_required` | 현재 active OP 없음 |
| 409 | `op_mismatch` | 요청 `opId`와 서버 current OP 불일치 |
| 413 | `photo_limit_exceeded` | 사진 수량 또는 파일 크기 제한 초과 |
