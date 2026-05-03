# L5-03 · 인증 계약서 — L5가 소비하는 S1-2 / S4 계약

상태: 초안. 담당: L5. 소비 대상: S1-2 (Account, Device & RBAC), S4 (Realtime Event Hub).

## 1. 목적

L5 (Marker / Photo / Notification) 구현 시 L2 팀이 제공하는 인증·권한·이벤트 계약을 정리한다.
이 문서는 L5가 **소비만 하는** 계약이며, L5가 직접 구현하지 않는다.

---

## 2. SecurityContext

L5 API는 S1-2가 제공하는 `SecurityContext`를 전제로 한다.

```json
{
  "accountId": "uuid",
  "accountType": "TEAM | PATROL_CAR | COMMAND",
  "affiliation": "MISSING_TEAM | SUPPORT_UNIT | LOCAL_POLICE",
  "roles": ["MISSING_TEAM_COMMANDER", "FIELD_COMMANDER", "MEMBER"],
  "channel": "APP | WEB | INTERNAL",
  "deviceId": "uuid | null"
}
```

### 2.1 L5에서 사용하는 Guard Annotation

| Annotation | 실패 코드 | L5 사용처 |
|---|---|---|
| `@RequireChannel(APP)` | `channel_not_allowed` | `POST /markers`, photo presign/finalize |
| `@RequireDevice` | `device_required` | marker/photo write |
| `@RequireDeviceRegistered` | `device_not_registered` | marker/photo write |
| `@RequireDeviceAssigned` | `device_not_assigned` | marker/photo write |
| `@RequireIncidentAccess` | `incident_access_denied`, `team_not_assigned` | 모든 L5 API |
| `@RequireCurrentOp` | `op_required`, `op_mismatch` | marker 생성 시 OP 연결 |
| `@IdempotentWrite` | `idempotency_mismatch` | marker 생성 |

### 2.2 L5 API Guard 조합

```
POST /markers
  = @RequireChannel(APP)
  + @RequireDevice
  + @RequireDeviceRegistered
  + @RequireDeviceAssigned
  + @RequireIncidentAccess
  + @RequireCurrentOp
  + @IdempotentWrite

POST /markers/{markerId}/photos/presign
POST /markers/{markerId}/photos/{photoId}/finalize
  = @RequireChannel(APP)
  + @RequireDevice
  + @RequireDeviceRegistered
  + @RequireDeviceAssigned
  + @RequireIncidentAccess
```

---

## 3. Account / Device / FCM Token Fixture

### 3.1 Account Fixture (S1-2 소유)

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

### 3.2 Device Fixture (S1-2 소유)

| Fixture ID | deviceType | accountId | FCM 수신 대상 |
|---|---|---|---|
| `dev-precinct-cmd-phone-01` | — | `acct-precinct-cmd` | ✗ (지휘 계정) |
| `dev-precinct-phone-01` | TEAM_PHONE | `acct-precinct-team` | ✓ |
| `dev-precinct-car-01` | PATROL_CAR_PHONE | `acct-precinct-car` | ✓ |
| `dev-alpha-cmd-phone-01` | — | `acct-cmd-alpha` | ✗ (지휘 계정) |
| `dev-alpha-phone-01` | TEAM_PHONE | `acct-team-alpha` | ✓ |
| `dev-support-cmd-phone-01` | — | `acct-support-cmd` | ✗ (지휘 계정) |
| `dev-support-phone-01` | TEAM_PHONE | `acct-support-team` | ✓ |
| `dev-support-car-01` | PATROL_CAR_PHONE | `acct-support-car` | ✓ |

> **주의**: 지휘 계정 `deviceId`는 웹 지휘·membership fixture 식별자로만 사용하며 Android FCM recipient로 고정하지 않는다.

### 3.3 FCM Token Query DTO

L5 `NotificationRecipientResolver`가 S1-2에서 소비하는 FCM token 조회 계약:

```java
// S1-2가 제공하는 port
public interface FcmTokenQueryPort {
    /**
     * 사건 배정 계정 중 ACTIVE FCM token을 가진 Device 목록 반환.
     * 지휘 계정 deviceId는 제외한다.
     */
    List<FcmRecipient> findActiveRecipients(UUID incidentId);
    
    /**
     * 특정 정책 기반 수신자 조회.
     * COMMANDERS_AND_FIELD_COMMANDERS: 지휘 계정 + 현장 지휘관
     * ALL_INCIDENT_ASSIGNED: 사건 배정 전체 TEAM_PHONE/PATROL_CAR_PHONE
     */
    List<FcmRecipient> findByPolicy(UUID incidentId, RecipientPolicy policy);
}
```

```json
// FcmRecipient DTO
{
  "accountId": "uuid",
  "deviceId": "uuid",
  "deviceType": "TEAM_PHONE | PATROL_CAR_PHONE",
  "encryptedToken": "string"
}
```

### 3.4 FCM Recipient Fixture

| 시나리오 | recipient_policy | 기대 수신 Device |
|---|---|---|
| 지원 요청 알림 (SC-08) | `COMMANDERS_AND_FIELD_COMMANDERS` | `dev-precinct-phone-01`, `dev-alpha-phone-01` (지휘관 역할 Device만) |
| 실종자 발견 알림 (SC-08) | `ALL_INCIDENT_ASSIGNED` | 사건 배정 전체 `TEAM_PHONE`/`PATROL_CAR_PHONE` |
| 지원 배정 알림 (SC-02) | 신규 배정 단말 | `dev-support-phone-01`, `dev-support-car-01` |

---

## 4. Event Envelope 계약 (S4 소유)

### 4.1 BaseEvent Envelope

S4가 정의하는 공통 이벤트 포맷. L5는 `payload` 필드만 소유한다.

```json
{
  "eventId": "uuid",
  "incidentId": "uuid",
  "type": "MARKER_CREATED | MARKER_UPDATED | MARKER_DELETED | SUPPORT_REQUEST_CREATED | PERSON_FOUND",
  "schemaVersion": 1,
  "serverTs": "2026-04-28T09:00:00+09:00",
  "payload": { }
}
```

### 4.2 L5가 발행하는 PublishRequest 이벤트 목록

| Event Type | Trigger | Payload Owner |
|---|---|---|
| `MARKER_CREATED` | `POST /markers` 성공 | S5 |
| `MARKER_UPDATED` | `PATCH /markers/{markerId}`, photo finalize | S5 |
| `MARKER_DELETED` | `DELETE /markers/{markerId}` | S5 |
| `SUPPORT_REQUEST_CREATED` | SUPPORT_REQUEST 마커 생성 | S5 |
| `PERSON_FOUND` | PERSON_FOUND 마커 생성 | S5 |

### 4.3 EventHub.publish 사용법

```java
// L5 domain service에서 사용하는 방식
@Transactional
public MarkerResponse createMarker(MarkerCreateCommand cmd) {
    Marker marker = markerRepository.save(/* ... */);
    
    // S4 EventHub port에 PublishRequest 전달
    eventHub.publish(PublishRequest.builder()
        .eventId(UUID.randomUUID())
        .incidentId(cmd.getIncidentId())
        .type("MARKER_CREATED")
        .schemaVersion(1)
        .domainRefType("marker")
        .domainRefId(marker.getId())
        .payload(markerPayload)
        .build());
    
    return toResponse(marker);
}
```

> **핵심 규칙**: `EventHub.publish`는 caller transaction 안에서 호출해야 하며, domain row와 event_outbox row가 같은 transaction에서 원자적으로 저장된다.

### 4.4 PublishRequest DTO

```json
{
  "eventId": "uuid",
  "incidentId": "uuid",
  "type": "string",
  "schemaVersion": 1,
  "domainRefType": "marker | photo | notification_delivery",
  "domainRefId": "uuid",
  "payload": { }
}
```

---

## 5. 필수 헤더 규칙

| 헤더 | 용도 | L5 write API 필수 여부 |
|---|---|---|
| `Authorization` | Bearer token | ✓ |
| `X-Client-Channel` | APP / WEB 채널 식별 | ✓ |
| `X-Device-Id` | Device 식별 | ✓ (APP 채널) |
| `Idempotency-Key` | 중복 쓰기 방지 | ✓ (POST /markers) |

---

## 6. 에러 코드 매트릭스

L5 API에서 발생 가능한 S1-2 기원 에러:

| HTTP | Error Code | 원인 |
|---|---|---|
| 400 | `device_required` | APP 채널인데 X-Device-Id 누락 |
| 403 | `channel_not_allowed` | WEB에서 앱 전용 API 호출 |
| 403 | `device_not_registered` | 미등록 Device |
| 403 | `device_not_assigned` | 해당 사건/OP 미배정 Device |
| 403 | `incident_access_denied` | 사건 접근 권한 없음 |
| 403 | `team_not_assigned` | 팀 미배정 |
| 409 | `incident_bootstrapping` | 사건 OPEN 전 write 시도 |
| 409 | `incident_closed` | 종료된 사건 write 시도 |
| 409 | `idempotency_mismatch` | 다른 body로 같은 key 재사용 |
| 409 | `op_required` | 현재 active OP 없음 |
| 409 | `op_mismatch` | 요청 opId ≠ 현재 active OP |
