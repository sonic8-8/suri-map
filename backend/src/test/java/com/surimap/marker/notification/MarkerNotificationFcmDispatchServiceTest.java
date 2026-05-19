package com.surimap.marker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.marker.dto.MarkerNotificationPublishRequestPayload;
import com.surimap.marker.event.MarkerEventIds;
import com.surimap.marker.notification.adapter.MockFcmDispatcher;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.marker.notification.service.MarkerNotificationFcmDispatchService;
import com.surimap.policephone.FcmTokenStatus;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarkerNotificationFcmDispatchService")
class MarkerNotificationFcmDispatchServiceTest {

  private static final UUID NOTIFICATION_ID =
      UUID.fromString(NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID);
  private static final UUID MARKER_ID = UUID.fromString(NotificationFixtures.PERSON_FOUND_MARKER_ID);
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID SOURCE_PHONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID RECIPIENT_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000205");

  @Test
  @DisplayName("marker_notification payload를 같은 eventId의 FCM data message로 발송한다")
  void dispatchesMarkerNotificationPayloadToActiveFcmTokens() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    MarkerNotificationFcmDispatchService service =
        new MarkerNotificationFcmDispatchService(new FixtureFcmTokenQuery(), dispatcher);
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
            List.of("11111111-1111-1111-1111-111111110001"),
            List.of(RECIPIENT_PHONE_ID.toString()),
            "PERSON_FOUND",
            "126.913400,35.163100");

    service.dispatchAfterCommit("PERSON_FOUND", payload);

    String eventId = MarkerEventIds.eventId("PERSON_FOUND", NOTIFICATION_ID, 1L).toString();
    var captured = dispatcher.findByEventId(eventId).orElseThrow();
    assertThat(captured.recipients()).containsExactly("token-recipient-phone");
    assertThat(captured.getPayloadField("type")).isEqualTo("PERSON_FOUND");
    assertThat(captured.getPayloadField("id")).isEqualTo(NOTIFICATION_ID.toString());
    assertThat(captured.getPayloadField("markerId")).isEqualTo(MARKER_ID.toString());
    assertThat(captured.getPayloadField("incidentId")).isEqualTo(INCIDENT_ID.toString());
    assertThat(captured.getPayloadField("status")).isEqualTo("SNAPSHOT_CREATED");
    assertThat(captured.getPayloadField("version")).isEqualTo(1L);
    assertThat(captured.recipientPolicePhoneIds()).containsExactly(RECIPIENT_PHONE_ID.toString());
  }

  @Test
  @DisplayName("선택 locationLabel이 없어도 FCM payload 기록을 실패시키지 않는다")
  void dispatchesWithoutOptionalLocationLabel() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    MarkerNotificationFcmDispatchService service =
        new MarkerNotificationFcmDispatchService(new FixtureFcmTokenQuery(), dispatcher);
    MarkerNotificationPublishRequestPayload payload =
        new MarkerNotificationPublishRequestPayload(
            NOTIFICATION_ID,
            MARKER_ID,
            INCIDENT_ID,
            OP_ID,
            SOURCE_PHONE_ID,
            "SNAPSHOT_CREATED",
            1L,
            "SUPPORT_REQUEST",
            "ROLE_BASED",
            List.of("11111111-1111-1111-1111-111111110001"),
            List.of(RECIPIENT_PHONE_ID.toString()),
            "SUPPORT_REQUEST",
            null);

    service.dispatchAfterCommit("SUPPORT_REQUEST_CREATED", payload);

    String eventId = MarkerEventIds.eventId("SUPPORT_REQUEST_CREATED", NOTIFICATION_ID, 1L).toString();
    var captured = dispatcher.findByEventId(eventId).orElseThrow();
    assertThat(captured.recipients()).containsExactly("token-recipient-phone");
    assertThat(captured.payload()).doesNotContainKey("locationLabel");
  }

  private static final class FixtureFcmTokenQuery implements FcmTokenQuery {

    @Override
    public List<FcmTokenRow> activeByPolicePhone(UUID policePhoneId) {
      if (!RECIPIENT_PHONE_ID.equals(policePhoneId)) {
        return List.of();
      }
      return List.of(
          new FcmTokenRow(
              UUID.fromString("90000000-0000-0000-0000-000000000001"),
              policePhoneId,
              "app-instance-1",
              "cipher:token-recipient-phone",
              "hash",
              FcmTokenStatus.ACTIVE,
              1L));
    }

    @Override
    public List<FcmTokenRow> activeByAccounts(List<UUID> accountIds) {
      return List.of();
    }
  }
}
