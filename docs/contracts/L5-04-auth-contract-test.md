# L5-04 · 인증 계약 검증 테스트 — Fixture 동작 검증 & 타 팀 공유 예제

상태: 초안. 담당: L5.

## 1. 목적

L5-03 인증 계약서에 정의된 fixture를 실제 코드로 검증하고, 타 팀이 L5 API를 소비할 때 참고할 예제를 제공한다.

---

## 2. SecurityContext Fixture 검증

### 2.1 앱 채널 마커 생성 — 정상 흐름

```java
@Test
@DisplayName("앱 채널 팀 계정이 마커를 생성할 수 있다")
void appChannel_teamAccount_canCreateMarker() {
    // given
    SecurityContext ctx = SecurityContext.builder()
        .accountId(UUID.fromString("acct-precinct-team"))
        .accountType(AccountType.TEAM)
        .affiliation(Affiliation.LOCAL_POLICE)
        .roles(List.of(Role.MEMBER))
        .channel(Channel.APP)
        .deviceId(UUID.fromString("dev-precinct-phone-01"))
        .build();

    MarkerCreateRequest request = MarkerCreateRequest.builder()
        .incidentId(UUID.fromString("inc-precinct-first-001"))
        .opId(UUID.fromString("op-precinct-001-op1"))
        .type(MarkerType.CLUE)
        .location(new GeoJsonPoint(126.956500, 37.571200))
        .clientTs(Instant.parse("2026-04-28T09:05:00Z"))
        .build();

    // when
    MarkerResponse response = markerService.create(ctx, request);

    // then
    assertThat(response.getId()).isNotNull();
    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(response.getOpId()).isEqualTo(request.getOpId());
    assertThat(response.getDeviceId()).isEqualTo(ctx.getDeviceId());
}
```

### 2.2 웹 채널 마커 생성 — 거부

```java
@Test
@DisplayName("웹 채널에서 마커 생성 시 channel_not_allowed")
void webChannel_markerCreate_rejected() {
    // given
    SecurityContext ctx = SecurityContext.builder()
        .accountId(UUID.fromString("acct-cmd-alpha"))
        .accountType(AccountType.COMMAND)
        .affiliation(Affiliation.MISSING_TEAM)
        .roles(List.of(Role.MISSING_TEAM_COMMANDER))
        .channel(Channel.WEB)
        .deviceId(null)
        .build();

    // when & then
    assertThatThrownBy(() -> markerService.create(ctx, anyRequest))
        .isInstanceOf(ChannelNotAllowedException.class)
        .hasFieldOrPropertyWithValue("errorCode", "channel_not_allowed");
}
```

### 2.3 미등록 Device — 거부

```java
@Test
@DisplayName("미등록 Device로 마커 생성 시 device_not_registered")
void unregisteredDevice_markerCreate_rejected() {
    // given: device.registered = false
    SecurityContext ctx = SecurityContext.builder()
        .accountId(UUID.fromString("acct-precinct-team"))
        .channel(Channel.APP)
        .deviceId(UUID.fromString("dev-unregistered-001"))
        .build();

    // when & then
    assertThatThrownBy(() -> markerService.create(ctx, anyRequest))
        .isInstanceOf(DeviceNotRegisteredException.class)
        .hasFieldOrPropertyWithValue("errorCode", "device_not_registered");
}
```

### 2.4 미배정 Device — 거부

```java
@Test
@DisplayName("사건 미배정 Device로 마커 생성 시 device_not_assigned")
void unassignedDevice_markerCreate_rejected() {
    // given: device는 등록됐지만 해당 사건/OP에 배정되지 않음
    SecurityContext ctx = SecurityContext.builder()
        .accountId(UUID.fromString("acct-precinct-team"))
        .channel(Channel.APP)
        .deviceId(UUID.fromString("dev-other-team-phone-01"))
        .build();

    // when & then
    assertThatThrownBy(() -> markerService.create(ctx, anyRequest))
        .isInstanceOf(DeviceNotAssignedException.class)
        .hasFieldOrPropertyWithValue("errorCode", "device_not_assigned");
}
```

---

## 3. OP 계약 검증

### 3.1 OP 존재 시 마커 생성

```java
@Test
@DisplayName("현재 active OP가 있으면 마커가 해당 OP에 귀속된다")
void activeOp_markerBindsToOp() {
    // given
    UUID opId = UUID.fromString("op-precinct-001-op1");
    // mock: operationalPeriodQueryPort.findCurrentOp() returns OP1
    
    // when
    MarkerResponse response = markerService.create(validCtx, requestWithOp(opId));
    
    // then
    assertThat(response.getOpId()).isEqualTo(opId);
}
```

### 3.2 OP 없을 때 거부

```java
@Test
@DisplayName("active OP가 없으면 op_required")
void noActiveOp_markerCreate_rejected() {
    // given: mock returns empty
    
    // when & then
    assertThatThrownBy(() -> markerService.create(validCtx, anyRequest))
        .isInstanceOf(OpRequiredException.class)
        .hasFieldOrPropertyWithValue("errorCode", "op_required");
}
```

### 3.3 OP 불일치 시 거부

```java
@Test
@DisplayName("요청 opId와 현재 active OP가 다르면 op_mismatch")
void opMismatch_markerCreate_rejected() {
    // given: currentOp = OP2, request.opId = OP1
    UUID currentOpId = UUID.fromString("op-precinct-001-op2");
    UUID requestOpId = UUID.fromString("op-precinct-001-op1");
    
    // when & then
    assertThatThrownBy(() -> markerService.create(validCtx, requestWithOp(requestOpId)))
        .isInstanceOf(OpMismatchException.class)
        .hasFieldOrPropertyWithValue("errorCode", "op_mismatch");
}
```

---

## 4. Geometry 계약 검증

### 4.1 정상 좌표 마커 생성

```java
@Test
@DisplayName("map_boundary 내 정상 좌표 마커 생성 성공")
void validPoint_insideBoundary_success() {
    // given: 하네스 기준 좌표
    GeoJsonPoint location = new GeoJsonPoint(126.956500, 37.571200);
    
    // when
    MarkerResponse response = markerService.create(validCtx, requestWithLocation(location));
    
    // then
    assertThat(response.getId()).isNotNull();
}
```

### 4.2 map_boundary 밖 좌표 거부

```java
@Test
@DisplayName("map_boundary 밖 좌표는 invalid_geometry")
void outsideBoundary_rejected() {
    // given: coord-outside-envelope fixture
    GeoJsonPoint location = new GeoJsonPoint(127.200000, 37.571200);
    
    // when & then
    assertThatThrownBy(() -> markerService.create(validCtx, requestWithLocation(location)))
        .isInstanceOf(InvalidGeometryException.class)
        .hasFieldOrPropertyWithValue("errorCode", "invalid_geometry");
}
```

### 4.3 lon/lat 뒤바뀐 좌표 거부

```java
@Test
@DisplayName("lon/lat가 뒤바뀌면 invalid_geometry")
void swappedLatLon_rejected() {
    // given: coord-latlon-swapped fixture
    GeoJsonPoint location = new GeoJsonPoint(37.571200, 126.956500);
    
    // when & then
    assertThatThrownBy(() -> markerService.create(validCtx, requestWithLocation(location)))
        .isInstanceOf(InvalidGeometryException.class);
}
```

---

## 5. Event Envelope 계약 검증

### 5.1 마커 생성 시 PublishRequest 발행 확인

```java
@Test
@DisplayName("마커 생성 시 MARKER_CREATED PublishRequest가 발행된다")
void markerCreate_publishesEvent() {
    // given
    MockEventHub mockEventHub = new MockEventHub();
    
    // when
    MarkerResponse response = markerService.create(validCtx, validRequest);
    
    // then
    PublishRequest publishedEvent = mockEventHub.getLastPublished();
    assertThat(publishedEvent.getType()).isEqualTo("MARKER_CREATED");
    assertThat(publishedEvent.getIncidentId()).isEqualTo(validRequest.getIncidentId());
    assertThat(publishedEvent.getDomainRefType()).isEqualTo("marker");
    assertThat(publishedEvent.getDomainRefId()).isEqualTo(response.getId());
    assertThat(publishedEvent.getSchemaVersion()).isEqualTo(1);
}
```

### 5.2 마커 생성 시 Event Payload 검증

```java
@Test
@DisplayName("MARKER_CREATED payload에 id/status/version/opId/deviceId가 포함된다")
void markerCreated_payloadContainsRequiredFields() {
    // given
    MockEventHub mockEventHub = new MockEventHub();
    
    // when
    MarkerResponse response = markerService.create(validCtx, validRequest);
    
    // then
    Map<String, Object> payload = mockEventHub.getLastPublished().getPayload();
    assertThat(payload).containsKeys("id", "status", "version", "opId", "deviceId", "type", "location");
    assertThat(payload.get("id")).isEqualTo(response.getId().toString());
    assertThat(payload.get("status")).isEqualTo("ACTIVE");
    assertThat(payload.get("version")).isEqualTo(1);
}
```

---

## 6. FCM Recipient 계약 검증

### 6.1 지원 요청 수신자 계산

```java
@Test
@DisplayName("지원 요청 알림은 지휘 계정과 현장 지휘관에게 전달된다")
void supportRequest_recipientIsCommandersOnly() {
    // given: SUPPORT_REQUEST marker 생성
    
    // when
    NotificationDelivery delivery = notificationService.createDelivery(supportRequestMarker);
    
    // then
    assertThat(delivery.getRecipientPolicy()).isEqualTo("COMMANDERS_AND_FIELD_COMMANDERS");
    assertThat(delivery.getRecipientDeviceIds())
        .contains(UUID.fromString("dev-precinct-phone-01"))
        .contains(UUID.fromString("dev-alpha-phone-01"))
        .doesNotContain(UUID.fromString("dev-precinct-cmd-phone-01"));  // 지휘 계정 제외
}
```

### 6.2 실종자 발견 수신자 계산

```java
@Test
@DisplayName("실종자 발견 알림은 사건 배정 전체 단말에 전달된다")
void personFound_recipientIsAllAssigned() {
    // given: PERSON_FOUND marker 생성
    
    // when
    NotificationDelivery delivery = notificationService.createDelivery(personFoundMarker);
    
    // then
    assertThat(delivery.getRecipientPolicy()).isEqualTo("ALL_INCIDENT_ASSIGNED");
    assertThat(delivery.getRecipientDeviceIds())
        .contains(UUID.fromString("dev-precinct-phone-01"))
        .contains(UUID.fromString("dev-precinct-car-01"))
        .contains(UUID.fromString("dev-alpha-phone-01"))
        .contains(UUID.fromString("dev-support-phone-01"))
        .contains(UUID.fromString("dev-support-car-01"));
}
```

---

## 7. 타 팀 공유 가이드

### 7.1 L2 팀 (인증·이벤트)

L5가 필요로 하는 S1-2 port:
- `SecurityContext` — 이미 제공 중
- `FcmTokenQueryPort.findActiveRecipients(incidentId)` — **L5가 소비**, L2 구현 필요
- `FcmTokenQueryPort.findByPolicy(incidentId, policy)` — **L5가 소비**, L2 구현 필요
- `EventHub.publish(PublishRequest)` — **L5가 소비**, L2 구현 필요

### 7.2 L3 팀 (지도·OP)

L5가 필요로 하는 S2/S8 port:
- `MapBoundaryQueryPort.findActive(incidentId)` — **L5가 소비**, L3 구현 필요
- `OperationalPeriodQueryPort.findCurrentOp(incidentId)` — **L5가 소비**, L3 구현 필요

### 7.3 Mock 사용 예시

L2/L3 실구현 전까지 L5는 mock으로 대체:

```java
// MockMapBoundaryQuery — L5 테스트용
public class MockMapBoundaryQuery implements MapBoundaryQueryPort {
    private static final Geometry HARNESS_BOUNDARY = createPolygon(
        new double[]{126.948000, 37.565000},
        new double[]{126.968000, 37.565000},
        new double[]{126.968000, 37.579000},
        new double[]{126.948000, 37.579000},
        new double[]{126.948000, 37.565000}
    );
    
    @Override
    public Optional<MapBoundary> findActive(UUID incidentId) {
        return Optional.of(new MapBoundary(
            UUID.fromString("mb-harness-001"),
            incidentId,
            HARNESS_BOUNDARY,
            "ACTIVE",
            1L
        ));
    }
}
```

```java
// MockOperationalPeriodQuery — L5 테스트용
public class MockOperationalPeriodQuery implements OperationalPeriodQueryPort {
    @Override
    public Optional<OperationalPeriodDto> findCurrentOp(UUID incidentId) {
        return Optional.of(new OperationalPeriodDto(
            UUID.fromString("op-precinct-001-op1"),
            incidentId,
            1,
            "ACTIVE",
            "BOOTSTRAP",
            null, null, "SYSTEM",
            Instant.parse("2026-04-28T00:00:00Z"),
            null, 1L,
            Instant.parse("2026-04-28T00:00:00Z"),
            Instant.parse("2026-04-28T00:00:00Z"),
            null
        ));
    }
}
```
