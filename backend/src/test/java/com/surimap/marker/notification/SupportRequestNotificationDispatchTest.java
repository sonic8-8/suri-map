package com.surimap.marker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.notification.adapter.MockFcmDispatcher;
import com.surimap.marker.notification.adapter.MockFcmDispatcher.CapturedDispatch;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.marker.notification.service.BoardToastEvidence;
import com.surimap.marker.notification.service.SupportRequestNotificationDispatchService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S14P31C106-71 L5-T06B support request FCM/toast convergence RED tests. */
@DisplayName("L5-T06B support request notification dispatch")
class SupportRequestNotificationDispatchTest {

  @Test
  @DisplayName("지원 요청 notification dispatch는 mock FCM capture와 board toast evidence로 수렴한다")
  void supportRequestDispatchCapturesFcmAndBoardToastEvidence() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    SupportRequestNotificationDispatchService service =
        new SupportRequestNotificationDispatchService(dispatcher);

    Map<String, Object> payload = NotificationFixtures.supportRequestPayload();

    BoardToastEvidence toast =
        service.dispatch(
            NotificationFixtures.SUPPORT_EVENT_ID,
            NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
            payload);

    CapturedDispatch capture =
        dispatcher.findByEventId(NotificationFixtures.SUPPORT_EVENT_ID).orElseThrow();
    assertThat(capture.recipients())
        .containsExactlyElementsOf(NotificationFixtures.SUPPORT_FCM_RECIPIENTS);
    assertThat(capture.getPayloadField("type")).isEqualTo("SUPPORT_REQUEST_CREATED");
    assertThat(capture.getPayloadField("id"))
        .isEqualTo(NotificationFixtures.SUPPORT_NOTIFICATION_ID);
    assertThat(capture.getPayloadField("markerId"))
        .isEqualTo(NotificationFixtures.SUPPORT_MARKER_ID);
    assertThat(capture.getPayloadField("incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(capture.getPayloadField("opId")).isEqualTo(NotificationFixtures.OP_ID);
    assertThat(capture.getPayloadField("policePhoneId"))
        .isEqualTo(NotificationFixtures.POLICE_PHONE_ID);
    assertThat(capture.recipientAccountIds())
        .isEqualTo(NotificationFixtures.SUPPORT_RECIPIENT_ACCOUNT_IDS);
    assertThat(capture.recipientPolicePhoneIds())
        .isEqualTo(NotificationFixtures.SUPPORT_RECIPIENT_POLICE_PHONE_IDS);

    assertThat(toast.slot()).isEqualTo("toast");
    assertThat(toast.eventId()).isEqualTo(NotificationFixtures.SUPPORT_EVENT_ID);
    assertThat(toast.type()).isEqualTo("SUPPORT_REQUEST_CREATED");
    assertThat(toast.id()).isEqualTo(NotificationFixtures.SUPPORT_NOTIFICATION_ID);
    assertThat(toast.markerId()).isEqualTo(NotificationFixtures.SUPPORT_MARKER_ID);
    assertThat(toast.incidentId()).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(toast.opId()).isEqualTo(NotificationFixtures.OP_ID);
    assertThat(toast.policePhoneId()).isEqualTo(NotificationFixtures.POLICE_PHONE_ID);
    assertThat(toast.status()).isEqualTo("SNAPSHOT_CREATED");
    assertThat(toast.version()).isEqualTo(1);
  }

  @Test
  @DisplayName("mock FCM 미수신 실패 주입 시 board toast evidence를 만들지 않는다")
  void supportRequestDispatchFailureDoesNotCreateBoardToastEvidence() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    dispatcher.injectFailureFor(NotificationFixtures.SUPPORT_EVENT_ID);
    SupportRequestNotificationDispatchService service =
        new SupportRequestNotificationDispatchService(dispatcher);

    assertThatThrownBy(
            () ->
                service.dispatch(
                    NotificationFixtures.SUPPORT_EVENT_ID,
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    NotificationFixtures.supportRequestPayload()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(NotificationFixtures.SUPPORT_EVENT_ID);

    assertThat(dispatcher.findByEventId(NotificationFixtures.SUPPORT_EVENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("필수 payload 필드가 빠지면 FCM capture 전에 거부한다")
  void invalidPayloadIsRejectedBeforeFcmCapture() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    SupportRequestNotificationDispatchService service =
        new SupportRequestNotificationDispatchService(dispatcher);
    Map<String, Object> payload = new HashMap<>(NotificationFixtures.supportRequestPayload());
    payload.remove("markerId");

    assertThatThrownBy(
            () ->
                service.dispatch(
                    NotificationFixtures.SUPPORT_EVENT_ID,
                    NotificationFixtures.SUPPORT_FCM_RECIPIENTS,
                    payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("payload.markerId");

    assertThat(dispatcher.findByEventId(NotificationFixtures.SUPPORT_EVENT_ID)).isEmpty();
  }
}
