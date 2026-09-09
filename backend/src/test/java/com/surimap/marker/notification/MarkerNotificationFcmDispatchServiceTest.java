package com.surimap.marker.notification;

import static com.surimap.account.AccountIdentityCatalog.ALPHA_TEAM_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.notification.adapter.MockFcmDispatcher;
import com.surimap.marker.notification.adapter.MockFcmDispatcher.CapturedDispatch;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.marker.notification.service.MarkerNotificationFcmDispatchService;
import com.surimap.policephone.PolicePhonePersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"surimap.object-storage.provider=mock", "fcm.provider=mock"})
class MarkerNotificationFcmDispatchServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID NOTIFICATION_ID =
      UUID.fromString(NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID);
  private static final UUID MARKER_ID =
      UUID.fromString(NotificationFixtures.PERSON_FOUND_MARKER_ID);
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID SOURCE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID RECIPIENT_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000205");
  private static final String FCM_TOKEN = "token-recipient-phone";

  @Autowired private MarkerNotificationFcmDispatchService fcmDispatchService;
  @Autowired private PolicePhonePersistenceService policePhonePersistenceService;
  @Autowired private MockFcmDispatcher fcmDispatcher;

  @BeforeEach
  void setUp() {
    fcmDispatcher.reset();
    jdbcTemplate.update("DELETE FROM fcm_token WHERE police_phone_id = ?", RECIPIENT_PHONE_ID);
    policePhonePersistenceService.registerFcmToken(
        RECIPIENT_PHONE_ID, ALPHA_TEAM_ID.toString(), "app-instance-1", FCM_TOKEN);
  }

  @Test
  @DisplayName("수신 업무폰에 활성 토큰이 있으면, 같은 이벤트 ID와 알림 내용을 FCM으로 전달한다")
  void dispatchAfterCommit_activeToken_sendsNotificationWithSameEventId() {
    // given: 실제 DB에 토큰을 등록한 업무폰을 발견 알림의 수신자로 지정한다.
    MarkerNotificationPublishRequestPayload payload =
        new MarkerNotificationPublishRequestPayload(
            NOTIFICATION_ID,
            MARKER_ID,
            INCIDENT_ID,
            OP_ID,
            SOURCE_PHONE_ID,
            "SNAPSHOT_CREATED",
            1L,
            "PERSON_FOUND",
            "ALL_INCIDENT_ASSIGNED",
            List.of(ALPHA_TEAM_ID.toString()),
            List.of(RECIPIENT_PHONE_ID.toString()),
            "PERSON_FOUND",
            "126.913400,35.163100",
            null,
            Instant.parse("2026-04-28T00:10:00Z"));

    // when: 트랜잭션 밖에서 호출하면 DB에서 토큰을 조회해 즉시 전달한다.
    fcmDispatchService.dispatchAfterCommit("PERSON_FOUND", payload);

    // then: 기존 이벤트 ID 규칙으로 미리 계산한 값과 알림 내용을 그대로 전달한다.
    assertThat(fcmDispatcher.getAllDispatches()).hasSize(1);
    CapturedDispatch captured = fcmDispatcher.getAllDispatches().get(0);
    assertThat(captured.eventId()).isEqualTo("efc55939-2b22-3752-b6f9-84e072ecc53a");
    assertThat(captured.recipients()).containsExactly(FCM_TOKEN);
    assertThat(captured.getPayloadField("type")).isEqualTo("PERSON_FOUND");
    assertThat(captured.getPayloadField("id")).isEqualTo(NOTIFICATION_ID.toString());
    assertThat(captured.getPayloadField("markerId")).isEqualTo(MARKER_ID.toString());
    assertThat(captured.getPayloadField("incidentId")).isEqualTo(INCIDENT_ID.toString());
    assertThat(captured.getPayloadField("opId")).isEqualTo(OP_ID.toString());
    assertThat(captured.getPayloadField("policePhoneId")).isEqualTo(SOURCE_PHONE_ID.toString());
    assertThat(captured.getPayloadField("status")).isEqualTo("SNAPSHOT_CREATED");
    assertThat(captured.getPayloadField("version")).isEqualTo(1L);
    assertThat(captured.getPayloadField("clientTs")).isEqualTo("2026-04-28T00:10:00Z");
    assertThat(captured.getPayloadField("locationLabel")).isEqualTo("126.913400,35.163100");
    assertThat(captured.getPayloadField("markerType")).isEqualTo("PERSON_FOUND");
    assertThat(captured.getPayloadField("recipientPolicy")).isEqualTo("ALL_INCIDENT_ASSIGNED");
    assertThat(captured.recipientAccountIds()).containsExactly(ALPHA_TEAM_ID.toString());
    assertThat(captured.recipientPolicePhoneIds()).containsExactly(RECIPIENT_PHONE_ID.toString());
  }

  @Test
  @DisplayName("위치 설명과 마커 기록 시각이 없으면, 해당 필드를 생략하고 FCM으로 전달한다")
  void dispatchAfterCommit_missingOptionalFields_sendsRequiredFieldsOnly() {
    // given: 수신 업무폰의 토큰은 DB에 등록되어 있고, 알림에는 선택 필드가 없다.
    MarkerNotificationPublishRequestPayload payload =
        new MarkerNotificationPublishRequestPayload(
            NOTIFICATION_ID,
            MARKER_ID,
            INCIDENT_ID,
            OP_ID,
            SOURCE_PHONE_ID,
            "SNAPSHOT_CREATED",
            1L,
            "SUPPORT_REQUEST_CREATED",
            NotificationFixtures.SUPPORT_RECIPIENT_POLICY,
            List.of(ALPHA_TEAM_ID.toString()),
            List.of(RECIPIENT_PHONE_ID.toString()),
            "SUPPORT_REQUEST",
            null);

    // when: 트랜잭션 밖에서 지원 요청 알림을 전달한다.
    fcmDispatchService.dispatchAfterCommit("SUPPORT_REQUEST_CREATED", payload);

    // then: 같은 업무폰으로 필수 정보만 전달하고 위치 설명·마커 기록 시각은 생략한다.
    assertThat(fcmDispatcher.getAllDispatches()).hasSize(1);
    CapturedDispatch captured = fcmDispatcher.getAllDispatches().get(0);
    assertThat(captured.eventId()).isEqualTo("95df6971-9d58-3fce-a0a8-0432be29ebd0");
    assertThat(captured.recipients()).containsExactly(FCM_TOKEN);
    assertThat(captured.payload())
        .containsEntry("type", "SUPPORT_REQUEST_CREATED")
        .containsEntry("id", NOTIFICATION_ID.toString())
        .containsEntry("markerId", MARKER_ID.toString())
        .doesNotContainKeys("locationLabel", "clientTs");
  }
}
