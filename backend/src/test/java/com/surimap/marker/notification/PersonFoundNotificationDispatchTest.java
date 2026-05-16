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

/** S14P31C106-71 L5-T07 person found FCM/toast convergence RED tests. */
@DisplayName("L5-T07 person found notification dispatch")
class PersonFoundNotificationDispatchTest {

  @Test
  @DisplayName("실종자 발견 notification dispatch는 mock FCM capture와 board toast evidence로 수렴한다")
  void personFoundDispatchCapturesFcmAndBoardToastEvidence() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    SupportRequestNotificationDispatchService service =
        new SupportRequestNotificationDispatchService(dispatcher);

    Map<String, Object> payload = NotificationFixtures.personFoundPayload();

    BoardToastEvidence toast =
        service.dispatch(
            NotificationFixtures.PERSON_FOUND_EVENT_ID,
            NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS,
            payload);

    CapturedDispatch capture =
        dispatcher.findByEventId(NotificationFixtures.PERSON_FOUND_EVENT_ID).orElseThrow();
    assertThat(capture.recipients())
        .containsExactlyElementsOf(NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS);
    assertThat(capture.getPayloadField("type")).isEqualTo("PERSON_FOUND");
    assertThat(capture.getPayloadField("id"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID);
    assertThat(capture.getPayloadField("markerId"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_MARKER_ID);
    assertThat(capture.getPayloadField("incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(capture.getPayloadField("opId")).isEqualTo(NotificationFixtures.OP_ID);
    assertThat(capture.getPayloadField("policePhoneId"))
        .isEqualTo(NotificationFixtures.POLICE_PHONE_ID);
    assertThat(capture.recipientAccountIds())
        .isEqualTo(NotificationFixtures.PERSON_FOUND_RECIPIENT_ACCOUNT_IDS);
    assertThat(capture.recipientPolicePhoneIds())
        .isEqualTo(NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS);

    assertThat(toast.slot()).isEqualTo("toast");
    assertThat(toast.eventId()).isEqualTo(NotificationFixtures.PERSON_FOUND_EVENT_ID);
    assertThat(toast.type()).isEqualTo("PERSON_FOUND");
    assertThat(toast.id()).isEqualTo(NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID);
    assertThat(toast.markerId()).isEqualTo(NotificationFixtures.PERSON_FOUND_MARKER_ID);
    assertThat(toast.incidentId()).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(toast.opId()).isEqualTo(NotificationFixtures.OP_ID);
    assertThat(toast.policePhoneId()).isEqualTo(NotificationFixtures.POLICE_PHONE_ID);
    assertThat(toast.status()).isEqualTo("SNAPSHOT_CREATED");
    assertThat(toast.version()).isEqualTo(1);
  }

  @Test
  @DisplayName("실종자 발견 mock FCM 미수신 실패 주입 시 board toast evidence를 만들지 않는다")
  void personFoundDispatchFailureDoesNotCreateBoardToastEvidence() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    dispatcher.injectFailureFor(NotificationFixtures.PERSON_FOUND_EVENT_ID);
    SupportRequestNotificationDispatchService service =
        new SupportRequestNotificationDispatchService(dispatcher);

    assertThatThrownBy(
            () ->
                service.dispatch(
                    NotificationFixtures.PERSON_FOUND_EVENT_ID,
                    NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS,
                    NotificationFixtures.personFoundPayload()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(NotificationFixtures.PERSON_FOUND_EVENT_ID);

    assertThat(dispatcher.findByEventId(NotificationFixtures.PERSON_FOUND_EVENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("실종자 발견 필수 payload 필드가 빠지면 FCM capture 전에 거부한다")
  void invalidPersonFoundPayloadIsRejectedBeforeFcmCapture() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    SupportRequestNotificationDispatchService service =
        new SupportRequestNotificationDispatchService(dispatcher);
    Map<String, Object> payload = new HashMap<>(NotificationFixtures.personFoundPayload());
    payload.remove("markerId");

    assertThatThrownBy(
            () ->
                service.dispatch(
                    NotificationFixtures.PERSON_FOUND_EVENT_ID,
                    NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS,
                    payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("payload.markerId");

    assertThat(dispatcher.findByEventId(NotificationFixtures.PERSON_FOUND_EVENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("기본 notification dispatch는 mock FCM 경계에서 capture로 수렴한다")
  void defaultDispatchUsesMockFcmBoundary() {
    MockFcmDispatcher dispatcher = new MockFcmDispatcher();
    SupportRequestNotificationDispatchService service =
        new SupportRequestNotificationDispatchService(dispatcher);

    BoardToastEvidence toast =
        service.dispatch(
            NotificationFixtures.PERSON_FOUND_EVENT_ID,
            NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS,
            NotificationFixtures.personFoundPayload());

    assertThat(toast.type()).isEqualTo("PERSON_FOUND");
    assertThat(dispatcher.findByEventId(NotificationFixtures.PERSON_FOUND_EVENT_ID)).isPresent();
  }
}
