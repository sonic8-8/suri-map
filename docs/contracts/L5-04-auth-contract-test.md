# L5-04 · L5 소비 계약 검증 예시

상태: 초안. 담당: L5.

## 1. 목적

L5-03, L5-05, L5-06에 정리한 소비 계약을 RED test 또는 fixture assertion으로 고정할 때 확인할 문자열 기준을 정리한다.
기준 문서에 없는 public API, fixture ID, event name, error code를 이 문서에서 새로 만들지 않는다.

## 2. PolicePhone Guard 검증

### 2.1 앱 채널 마커 생성 정상 흐름

```java
@Test
@DisplayName("APP 채널의 배정 PolicePhone이 /api/markers를 호출하면 marker가 생성된다")
void appChannel_assignedPolicePhone_canCreateMarker() {
    SecurityContext ctx = SecurityContext.builder()
        .accountId("acct-precinct-team")
        .accountType(AccountType.TEAM)
        .affiliation(Affiliation.LOCAL_POLICE)
        .roles(List.of(Role.MEMBER))
        .channel(Channel.APP)
        .policePhoneId("dev-precinct-phone-01")
        .build();

    MarkerCreateRequest request = MarkerCreateRequest.builder()
        .incidentId("inc-precinct-first-001")
        .opId("op-precinct-001-op1")
        .type(MarkerType.CLUE)
        .location(new GeoJsonPoint(126.956500, 37.571200))
        .clientTs(Instant.parse("2026-04-28T09:05:00+09:00"))
        .build();

    MarkerResponse response = markerService.create(ctx, request);

    assertThat(response.getStatus()).isEqualTo("ACTIVE");
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(response.getOpId()).isEqualTo("op-precinct-001-op1");
    assertThat(response.getPolicePhoneId()).isEqualTo("dev-precinct-phone-01");
}
```

### 2.2 웹 채널 마커 생성 거부

```java
@Test
@DisplayName("WEB 채널에서 /api/markers 호출 시 channel_not_allowed")
void webChannel_markerCreate_rejected() {
    SecurityContext ctx = SecurityContext.builder()
        .accountId("acct-cmd-alpha")
        .accountType(AccountType.COMMAND)
        .affiliation(Affiliation.MISSING_TEAM)
        .roles(List.of(Role.MISSING_TEAM_COMMANDER))
        .channel(Channel.WEB)
        .policePhoneId(null)
        .build();

    assertThatThrownBy(() -> markerService.create(ctx, anyRequest))
        .isInstanceOf(ChannelNotAllowedException.class)
        .hasFieldOrPropertyWithValue("errorCode", "channel_not_allowed");
}
```

### 2.3 미등록 PolicePhone 거부

```java
@Test
@DisplayName("미등록 PolicePhone으로 마커 생성 시 police_phone_not_registered")
void unregisteredPolicePhone_markerCreate_rejected() {
    SecurityContext ctx = SecurityContext.builder()
        .accountId("acct-precinct-team")
        .channel(Channel.APP)
        .policePhoneId("dev-unregistered-001")
        .build();

    assertThatThrownBy(() -> markerService.create(ctx, anyRequest))
        .isInstanceOf(PolicePhoneNotRegisteredException.class)
        .hasFieldOrPropertyWithValue("errorCode", "police_phone_not_registered");
}
```

### 2.4 미배정 PolicePhone 거부

```java
@Test
@DisplayName("사건/OP 미배정 PolicePhone으로 마커 생성 시 police_phone_not_assigned")
void unassignedPolicePhone_markerCreate_rejected() {
    SecurityContext ctx = SecurityContext.builder()
        .accountId("acct-precinct-team")
        .channel(Channel.APP)
        .policePhoneId("dev-not-assigned-001")
        .build();

    assertThatThrownBy(() -> markerService.create(ctx, anyRequest))
        .isInstanceOf(PolicePhoneNotAssignedException.class)
        .hasFieldOrPropertyWithValue("errorCode", "police_phone_not_assigned");
}
```

## 3. OP 계약 검증

```java
@Test
@DisplayName("현재 active OP가 있으면 marker.opId는 current OP와 같다")
void activeOp_markerBindsToOp() {
    String opId = "op-precinct-001-op1";

    MarkerResponse response = markerService.create(validCtx, requestWithOp(opId));

    assertThat(response.getOpId()).isEqualTo(opId);
}
```

```java
@Test
@DisplayName("active OP가 없으면 op_required")
void noActiveOp_markerCreate_rejected() {
    assertThatThrownBy(() -> markerService.create(validCtx, anyRequest))
        .isInstanceOf(OpRequiredException.class)
        .hasFieldOrPropertyWithValue("errorCode", "op_required");
}
```

```java
@Test
@DisplayName("요청 opId와 서버 current OP가 다르면 op_mismatch")
void opMismatch_markerCreate_rejected() {
    assertThatThrownBy(() -> markerService.create(validCtx, requestWithOp("op-precinct-001-op1")))
        .isInstanceOf(OpMismatchException.class)
        .hasFieldOrPropertyWithValue("errorCode", "op_mismatch");
}
```

## 4. Geometry 계약 검증

```java
@Test
@DisplayName("overall_search_area 내부 정상 marker Point는 통과한다")
void validPoint_insideOverallSearchArea_success() {
    GeoJsonPoint location = new GeoJsonPoint(126.956500, 37.571200);

    MarkerResponse response = markerService.create(validCtx, requestWithLocation(location));

    assertThat(response.getId()).isNotNull();
}
```

```java
@Test
@DisplayName("overall_search_area 밖 좌표는 invalid_geometry")
void outsideOverallSearchArea_rejected() {
    GeoJsonPoint location = new GeoJsonPoint(127.200000, 37.571200);

    assertThatThrownBy(() -> markerService.create(validCtx, requestWithLocation(location)))
        .isInstanceOf(InvalidGeometryException.class)
        .hasFieldOrPropertyWithValue("errorCode", "invalid_geometry");
}
```

```java
@Test
@DisplayName("lon/lat가 뒤바뀌면 invalid_geometry")
void swappedLatLon_rejected() {
    GeoJsonPoint location = new GeoJsonPoint(37.571200, 126.956500);

    assertThatThrownBy(() -> markerService.create(validCtx, requestWithLocation(location)))
        .isInstanceOf(InvalidGeometryException.class)
        .hasFieldOrPropertyWithValue("errorCode", "invalid_geometry");
}
```

## 5. Marker Event 계약 검증

```java
@Test
@DisplayName("마커 생성 시 MARKER_CREATED PublishRequest가 발행된다")
void markerCreate_publishesEvent() {
    MarkerResponse response = markerService.create(validCtx, validRequest);

    PublishRequest publishedEvent = mockEventHub.getLastPublished();
    assertThat(publishedEvent.getType()).isEqualTo("MARKER_CREATED");
    assertThat(publishedEvent.getIncidentId()).isEqualTo(validRequest.getIncidentId());
    assertThat(publishedEvent.getDomainRefType()).isEqualTo("marker");
    assertThat(publishedEvent.getDomainRefId()).isEqualTo(response.getId());
    assertThat(publishedEvent.getSchemaVersion()).isEqualTo(1);
}
```

```java
@Test
@DisplayName("MARKER_CREATED payload에는 id/status/version/opId/policePhoneId가 포함된다")
void markerCreated_payloadContainsRequiredFields() {
    markerService.create(validCtx, validRequest);

    Map<String, Object> payload = mockEventHub.getLastPublished().getPayload();
    assertThat(payload).containsKeys("id", "status", "version", "opId", "policePhoneId", "type", "location");
}
```

## 6. Photo upload-url/attach 계약 검증

```java
@Test
@DisplayName("사진 upload-url은 mock object storage uploadUrl을 반환한다")
void photoUploadUrl_usesMockObjectStorage() {
    PhotoUploadUrlResponse response = photoService.createUploadUrl(
        "mk-precinct-clue-001",
        new PhotoUploadUrlRequest("image/jpeg", 1_048_576, "sha256")
    );

    assertThat(response.getPhotoId()).isEqualTo("photo-precinct-clue-001");
    assertThat(response.getUploadUrl()).startsWith("http://127.0.0.1:18080/mock-upload/");
    assertThat(response.getMaxSizeBytes()).isEqualTo(10_485_760);
}
```

```java
@Test
@DisplayName("사진 attach 성공은 MARKER_UPDATED.photoDelta로 수렴한다")
void photoAttach_publishesMarkerUpdatedPhotoDelta() {
    PhotoAttachResponse response = photoService.attach(
        "mk-precinct-clue-001",
        "photo-precinct-clue-001",
        validAttachRequest
    );

    PublishRequest event = mockEventHub.getLastPublished();
    assertThat(response.getStatus()).isEqualTo("ATTACHED");
    assertThat(response.getMarkerVersion()).isEqualTo(2L);
    assertThat(event.getType()).isEqualTo("MARKER_UPDATED");
    assertThat(event.getPayload()).containsEntry("id", "mk-precinct-clue-001");
    assertThat(event.getPayload()).extracting("photoDelta.photoId").isEqualTo("photo-precinct-clue-001");
    assertThat(event.getPayload()).extracting("photoDelta.status").isEqualTo("ATTACHED");
    assertThat(event.getPayload()).extracting("photoDelta.version").isEqualTo(2);
}
```

Mock object storage fixture는 `mock://object-storage/suri-map-harness`와 `http://127.0.0.1:18080/mock-upload/{photoId}`만 사용한다. 외부 `s3.amazonaws.com` 또는 실제 bucket endpoint 호출이 관찰되면 SC-06 RED 실패다.

## 7. FCM Recipient 계약 검증

```java
@Test
@DisplayName("SC-08 지원 요청 알림은 canonical mock FCM recipient에만 전달된다")
void supportRequest_recipientMatchesFixture() {
    NotificationDelivery delivery = notificationService.createDelivery(supportRequestMarker);

    assertThat(delivery.getRecipientPolicy()).isEqualTo("COMMANDERS_AND_FIELD_COMMANDERS");
    assertThat(delivery.getRecipientPolicePhoneIds())
        .containsExactlyInAnyOrder("dev-alpha-phone-01", "dev-support-car-01", "dev-support-phone-01");
    assertThat(mockFcmDispatcher.getRecipients("evt-s5-support-request-001"))
        .containsExactlyInAnyOrder("fcm:dev-alpha-phone-01", "fcm:dev-support-car-01", "fcm:dev-support-phone-01");
}
```

```java
@Test
@DisplayName("SC-08 실종자 발견 알림은 marker/toast/FCM payload id/status/version을 맞춘다")
void personFound_payloadConverges() {
    NotificationDelivery delivery = notificationService.createDelivery(personFoundMarker);

    assertThat(delivery.getRecipientPolicy()).isEqualTo("ALL_INCIDENT_ASSIGNED");
    assertThat(delivery.getRecipientPolicePhoneIds())
        .contains("dev-precinct-car-01", "dev-precinct-phone-01", "dev-alpha-phone-01", "dev-support-car-01", "dev-support-phone-01");
    assertThat(mockFcmDispatcher.getRecipients("evt-s5-person-found-001"))
        .containsExactlyInAnyOrder("fcm:dev-alpha-phone-01", "fcm:dev-support-car-01", "fcm:dev-support-phone-01");
}
```

## 8. 타 팀 공유 가이드

### 8.1 L2 팀 (인증·이벤트)

L5가 소비하는 S1-2/S4 계약:

- `SecurityContext`
- `FcmTokenQuery.activeByPolicePhone(policePhoneId)`
- `EventHub.publish(PublishRequest)`
- `EventFanout -> FcmDispatcher.send(recipients, payload)` 호출 경계

### 8.2 L3 팀 (지도·OP)

L5가 소비하는 S2/S8 계약:

- `SearchAreaQuery.overallOf(incidentId)`
- `OperationalPeriodQuery.findCurrentOp(incidentId)`

### 8.3 Mock 사용 예시

```java
public class MockSearchAreaQuery implements SearchAreaQuery {
    private static final Geometry HARNESS_OVERALL_SEARCH_AREA = createPolygon(
        new double[]{126.948000, 37.565000},
        new double[]{126.968000, 37.565000},
        new double[]{126.968000, 37.579000},
        new double[]{126.948000, 37.579000},
        new double[]{126.948000, 37.565000}
    );

    @Override
    public Optional<OverallSearchAreaResult> overallOf(String incidentId) {
        return Optional.of(new OverallSearchAreaResult(
            "osa-precinct-001-v1",
            incidentId,
            "ACTIVE",
            1L,
            HARNESS_OVERALL_SEARCH_AREA
        ));
    }
}
```

```java
public class MockOperationalPeriodQuery implements OperationalPeriodQuery {
    @Override
    public Optional<OperationalPeriodDto> findCurrentOp(String incidentId) {
        return Optional.of(new OperationalPeriodDto(
            "op-precinct-001-op1",
            incidentId,
            1,
            "ACTIVE",
            "INITIAL",
            null,
            "SYSTEM",
            Instant.parse("2026-04-28T09:00:00+09:00"),
            null,
            1L
        ));
    }
}
```
