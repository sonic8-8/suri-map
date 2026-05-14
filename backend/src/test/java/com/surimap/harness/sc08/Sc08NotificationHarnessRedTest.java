package com.surimap.harness.sc08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.marker.notification.fixture.NotificationFixtures;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L5-T09B RED: SC-08 support request / person-found notification harness runner contract.
 *
 * <p>The runner is intentionally absent in RED. GREEN should implement only the harness runner with
 * existing S5 notification policy/dispatch services, S1-2 auth + FCM token fixtures, S4 event
 * mocks, S6 outbox/idempotency probes, and S3-2 board marker/toast convergence probes. It must not
 * add production FCM infrastructure.
 */
@DisplayName("SC-08 notification harness runner RED")
class Sc08NotificationHarnessRedTest {

  private static final String RUNNER_CLASS =
      "com.surimap.harness.sc08.Sc08NotificationHarnessRunner";

  @Test
  @DisplayName("SUPPORT_REQUEST notification flow proves auth, marker event, FCM, and board toast")
  void supportRequestFlowProducesNotificationConvergenceEvidence() {
    Object result = run("runSupportRequestNotificationFlow");

    assertCommonScenario(result, "SUPPORT_REQUEST");

    Object request = call(result, "markerRequest");
    assertThat(value(request, "apiPath")).isEqualTo("/api/markers");
    assertThat(value(request, "httpMethod")).isEqualTo("POST");
    assertThat(value(request, "markerType")).isEqualTo("SUPPORT_REQUEST");
    assertThat(value(request, "supportRequestType")).isEqualTo("DRONE");
    assertThat(value(request, "incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(value(request, "opId")).isEqualTo(NotificationFixtures.OP_ID);

    assertAuthEvidence(result);

    Object markerEvent = call(result, "markerCreatedEvent");
    assertThat(value(markerEvent, "type")).isEqualTo("MARKER_CREATED");
    assertThat(value(markerEvent, "id")).isEqualTo(NotificationFixtures.SUPPORT_MARKER_ID);
    assertThat(value(markerEvent, "fixtureMarkerId"))
        .isEqualTo(NotificationFixtures.SUPPORT_MARKER_ALIAS);
    assertThat(value(markerEvent, "markerType")).isEqualTo("SUPPORT_REQUEST");
    assertThat(value(markerEvent, "status")).isEqualTo("ACTIVE");
    assertThat(value(markerEvent, "version")).isEqualTo("1");
    assertThat(value(markerEvent, "opId")).isEqualTo(NotificationFixtures.OP_ID);
    assertThat(value(markerEvent, "policePhoneId")).isEqualTo(NotificationFixtures.POLICE_PHONE_ID);
    assertThat(booleanValue(markerEvent, "eventDispatchJobCaptured")).isTrue();
    assertThat(value(markerEvent, "eventDispatchEntityId")).isEqualTo(value(markerEvent, "id"));
    assertThat(booleanValue(markerEvent, "ssePayloadCaptured")).isTrue();
    assertThat(value(markerEvent, "markerRowCount")).isEqualTo("1");

    Object notificationEvent = call(result, "notificationEvent");
    assertThat(value(notificationEvent, "eventId"))
        .isEqualTo(NotificationFixtures.SUPPORT_EVENT_ID);
    assertThat(value(notificationEvent, "type")).isEqualTo("SUPPORT_REQUEST_CREATED");
    assertThat(value(notificationEvent, "id")).isEqualTo(NotificationFixtures.SUPPORT_NOTIFICATION_ID);
    assertThat(value(notificationEvent, "fixtureNotificationId"))
        .isEqualTo(NotificationFixtures.SUPPORT_NOTIFICATION_ALIAS);
    assertThat(value(notificationEvent, "markerId")).isEqualTo(value(markerEvent, "id"));
    assertThat(value(notificationEvent, "fixtureMarkerId"))
        .isEqualTo(NotificationFixtures.SUPPORT_MARKER_ALIAS);
    assertThat(value(notificationEvent, "incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(value(notificationEvent, "status")).isEqualTo("SNAPSHOT_CREATED");
    assertThat(value(notificationEvent, "version")).isEqualTo("1");
    assertThat(value(notificationEvent, "opId")).isEqualTo(value(markerEvent, "opId"));
    assertThat(value(notificationEvent, "policePhoneId"))
        .isEqualTo(value(markerEvent, "policePhoneId"));
    assertThat(value(notificationEvent, "markerStatus")).isEqualTo("ACTIVE");
    assertThat(value(notificationEvent, "markerVersion")).isEqualTo("1");
    assertThat(booleanValue(notificationEvent, "publishRequestCaptured")).isTrue();
    assertThat(booleanValue(notificationEvent, "eventDispatchJobCaptured")).isTrue();
    assertThat(value(notificationEvent, "eventDispatchEntityId"))
        .isEqualTo(value(notificationEvent, "id"));
    assertThat(value(notificationEvent, "sseType")).isEqualTo("SUPPORT_REQUEST_CREATED");
    assertThat(value(notificationEvent, "sseEntityId")).isEqualTo(value(notificationEvent, "id"));
    assertThat(value(notificationEvent, "sseVersion")).isEqualTo("1");

    assertFcmEvidence(
        result,
        "SUPPORT_REQUEST_CREATED",
        NotificationFixtures.SUPPORT_EVENT_ID,
        notificationEvent,
        NotificationFixtures.SUPPORT_RECIPIENT_POLICY,
        NotificationFixtures.SUPPORT_RECIPIENT_ACCOUNT_IDS,
        NotificationFixtures.SUPPORT_RECIPIENT_POLICE_PHONE_IDS,
        NotificationFixtures.SUPPORT_FCM_RECIPIENTS);
    assertBoardToastEvidence(
        result,
        "SUPPORT_REQUEST_CREATED",
        NotificationFixtures.SUPPORT_EVENT_ID,
        notificationEvent,
        NotificationFixtures.SUPPORT_NOTIFICATION_ID,
        NotificationFixtures.SUPPORT_MARKER_ID);
  }

  @Test
  @DisplayName("PERSON_FOUND notification flow proves auth, marker event, FCM, and board toast")
  void personFoundFlowProducesNotificationConvergenceEvidence() {
    Object result = run("runPersonFoundNotificationFlow");

    assertCommonScenario(result, "PERSON_FOUND");

    Object request = call(result, "markerRequest");
    assertThat(value(request, "apiPath")).isEqualTo("/api/markers");
    assertThat(value(request, "httpMethod")).isEqualTo("POST");
    assertThat(value(request, "markerType")).isEqualTo("PERSON_FOUND");
    assertThat(booleanValue(request, "supportRequestTypeAbsent")).isTrue();
    assertThat(value(request, "incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(value(request, "opId")).isEqualTo(NotificationFixtures.OP_ID);

    assertAuthEvidence(result);

    Object markerEvent = call(result, "markerCreatedEvent");
    assertThat(value(markerEvent, "type")).isEqualTo("MARKER_CREATED");
    assertThat(value(markerEvent, "id")).isEqualTo(NotificationFixtures.PERSON_FOUND_MARKER_ID);
    assertThat(value(markerEvent, "fixtureMarkerId"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_MARKER_ALIAS);
    assertThat(value(markerEvent, "markerType")).isEqualTo("PERSON_FOUND");
    assertThat(value(markerEvent, "status")).isEqualTo("ACTIVE");
    assertThat(value(markerEvent, "version")).isEqualTo("1");
    assertThat(value(markerEvent, "opId")).isEqualTo(NotificationFixtures.OP_ID);
    assertThat(value(markerEvent, "policePhoneId")).isEqualTo(NotificationFixtures.POLICE_PHONE_ID);
    assertThat(booleanValue(markerEvent, "eventDispatchJobCaptured")).isTrue();
    assertThat(value(markerEvent, "eventDispatchEntityId")).isEqualTo(value(markerEvent, "id"));
    assertThat(booleanValue(markerEvent, "ssePayloadCaptured")).isTrue();
    assertThat(value(markerEvent, "markerRowCount")).isEqualTo("1");

    Object notificationEvent = call(result, "notificationEvent");
    assertThat(value(notificationEvent, "eventId"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_EVENT_ID);
    assertThat(value(notificationEvent, "type")).isEqualTo("PERSON_FOUND");
    assertThat(value(notificationEvent, "id"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID);
    assertThat(value(notificationEvent, "fixtureNotificationId"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_NOTIFICATION_ALIAS);
    assertThat(value(notificationEvent, "markerId")).isEqualTo(value(markerEvent, "id"));
    assertThat(value(notificationEvent, "fixtureMarkerId"))
        .isEqualTo(NotificationFixtures.PERSON_FOUND_MARKER_ALIAS);
    assertThat(value(notificationEvent, "incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(value(notificationEvent, "status")).isEqualTo("SNAPSHOT_CREATED");
    assertThat(value(notificationEvent, "version")).isEqualTo("1");
    assertThat(value(notificationEvent, "opId")).isEqualTo(value(markerEvent, "opId"));
    assertThat(value(notificationEvent, "policePhoneId"))
        .isEqualTo(value(markerEvent, "policePhoneId"));
    assertThat(value(notificationEvent, "markerStatus")).isEqualTo("ACTIVE");
    assertThat(value(notificationEvent, "markerVersion")).isEqualTo("1");
    assertThat(booleanValue(notificationEvent, "publishRequestCaptured")).isTrue();
    assertThat(booleanValue(notificationEvent, "eventDispatchJobCaptured")).isTrue();
    assertThat(value(notificationEvent, "eventDispatchEntityId"))
        .isEqualTo(value(notificationEvent, "id"));
    assertThat(value(notificationEvent, "sseType")).isEqualTo("PERSON_FOUND");
    assertThat(value(notificationEvent, "sseEntityId")).isEqualTo(value(notificationEvent, "id"));
    assertThat(value(notificationEvent, "sseVersion")).isEqualTo("1");

    assertFcmEvidence(
        result,
        "PERSON_FOUND",
        NotificationFixtures.PERSON_FOUND_EVENT_ID,
        notificationEvent,
        NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICY,
        NotificationFixtures.PERSON_FOUND_RECIPIENT_ACCOUNT_IDS,
        NotificationFixtures.PERSON_FOUND_RECIPIENT_POLICE_PHONE_IDS,
        NotificationFixtures.PERSON_FOUND_FCM_RECIPIENTS);
    assertBoardToastEvidence(
        result,
        "PERSON_FOUND",
        NotificationFixtures.PERSON_FOUND_EVENT_ID,
        notificationEvent,
        NotificationFixtures.PERSON_FOUND_NOTIFICATION_ID,
        NotificationFixtures.PERSON_FOUND_MARKER_ID);
  }

  @Test
  @DisplayName("mock FCM 미수신 실패 주입은 board toast convergence를 실패로 판정한다")
  void missingMockFcmCaptureFailsBeforeBoardToastConvergence() {
    Object result = run("runMockFcmMissingFailureInjection");

    assertCommonScenario(result, "FCM_FAILURE_INJECTION");
    Object failure = call(result, "failureInjection");
    assertThat(value(failure, "kind")).isEqualTo("MOCK_FCM_NOT_RECEIVED");
    assertThat(listValue(failure, "injectedEventTypes"))
        .containsExactly("SUPPORT_REQUEST_CREATED", "PERSON_FOUND");
    assertThat(booleanValue(failure, "restDbCommitted")).isTrue();
    assertThat(booleanValue(failure, "eventDispatchJobCaptured")).isTrue();
    assertThat(booleanValue(failure, "mockFcmCaptured")).isFalse();
    assertThat(booleanValue(failure, "boardToastCreated")).isFalse();
    assertThat(booleanValue(failure, "boardConvergenceFailed")).isTrue();
    assertThat(booleanValue(failure, "externalFcmCalled")).isFalse();
  }

  @Test
  @DisplayName("지원하지 않는 외부 push infrastructure 주입은 mock FCM 경계에서 거부한다")
  void unsupportedExternalPushInjectionIsRejectedAtMockBoundary() {
    Object result = run("runUnsupportedExternalPushInjection");

    assertCommonScenario(result, "EXTERNAL_PUSH_FAILURE_INJECTION");
    Object failure = call(result, "failureInjection");
    assertThat(value(failure, "kind")).isEqualTo("UNSUPPORTED_EXTERNAL_PUSH");
    assertThat(booleanValue(failure, "externalPushRequested")).isTrue();
    assertThat(booleanValue(failure, "unsupportedExternalPushRejected")).isTrue();
    assertThat(booleanValue(failure, "productionFcmAdapterLoaded")).isFalse();
    assertThat(booleanValue(failure, "externalFcmCalled")).isFalse();
    assertThat(booleanValue(failure, "mockFcmCaptured")).isFalse();
    assertThat(booleanValue(failure, "boardToastCreated")).isFalse();
  }

  private static void assertCommonScenario(Object result, String flow) {
    assertThat(value(result, "scenarioId")).isEqualTo("SC-08");
    assertThat(value(result, "flow")).isEqualTo(flow);
    assertThat(value(result, "incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(value(result, "opId")).isEqualTo(NotificationFixtures.OP_ID);
  }

  private static void assertAuthEvidence(Object result) {
    Object auth = call(result, "auth");
    assertThat(value(auth, "channel")).isEqualTo("APP");
    assertThat(value(auth, "accountId")).isEqualTo(NotificationFixtures.ACCOUNT_ID);
    assertThat(value(auth, "policePhoneId")).isEqualTo(NotificationFixtures.POLICE_PHONE_ID);
    assertThat(booleanValue(auth, "authorizationChecked")).isTrue();
    assertThat(booleanValue(auth, "channelGuardChecked")).isTrue();
    assertThat(booleanValue(auth, "policePhoneRegistered")).isTrue();
    assertThat(booleanValue(auth, "policePhoneAssignedToIncident")).isTrue();
    assertThat(booleanValue(auth, "incidentOpen")).isTrue();
    assertThat(booleanValue(auth, "currentOpMatched")).isTrue();
    assertThat(booleanValue(auth, "idempotencyKeyRequired")).isTrue();
    assertThat(value(auth, "webChannelRejectionStatus")).isEqualTo("403");
    assertThat(value(auth, "webChannelRejectionError")).isEqualTo("channel_not_allowed");
    assertThat(value(auth, "idempotencyRejectionStatus")).isEqualTo("409");
    assertThat(value(auth, "idempotencyRejectionError")).isEqualTo("write_conflict");
    assertThat(booleanValue(auth, "idempotencyPreventedWrite")).isTrue();
  }

  private static void assertFcmEvidence(
      Object result,
      String expectedType,
      String expectedEventId,
      Object notificationEvent,
      String expectedRecipientPolicy,
      List<String> expectedRecipientAccountIds,
      List<String> expectedRecipientPolicePhoneIds,
      List<String> expectedFcmRecipients) {
    Object fcm = call(result, "fcm");
    assertThat(value(fcm, "dispatcher")).isEqualTo("mock FcmDispatcher");
    assertThat(value(fcm, "eventId")).isEqualTo(expectedEventId);
    assertThat(listValue(fcm, "recipients")).containsExactlyElementsOf(expectedFcmRecipients);
    assertThat(listValue(fcm, "recipientAccountIds"))
        .containsExactlyElementsOf(expectedRecipientAccountIds);
    assertThat(listValue(fcm, "recipientPolicePhoneIds"))
        .containsExactlyElementsOf(expectedRecipientPolicePhoneIds);
    assertThat(booleanValue(fcm, "payloadPiiFree")).isTrue();
    assertThat(booleanValue(fcm, "externalFcmCalled")).isFalse();
    assertThat(booleanValue(fcm, "foregroundDataMessageCaptured")).isTrue();
    assertThat(booleanValue(fcm, "backgroundDataMessageCaptured")).isTrue();
    assertThat(booleanValue(fcm, "duplicateEventSuppressed")).isTrue();

    Map<String, Object> payload = mapValue(fcm, "payload");
    assertThat(payload)
        .containsEntry("type", expectedType)
        .containsEntry("id", value(notificationEvent, "id"))
        .containsEntry("markerId", value(notificationEvent, "markerId"))
        .containsEntry("incidentId", value(notificationEvent, "incidentId"))
        .containsEntry("opId", value(notificationEvent, "opId"))
        .containsEntry("policePhoneId", value(notificationEvent, "policePhoneId"))
        .containsEntry("status", "SNAPSHOT_CREATED")
        .containsEntry("version", 1)
        .containsEntry("recipientPolicy", expectedRecipientPolicy)
        .containsEntry("recipientAccountIds", expectedRecipientAccountIds)
        .containsEntry("recipientPolicePhoneIds", expectedRecipientPolicePhoneIds);
  }

  private static void assertBoardToastEvidence(
      Object result,
      String expectedType,
      String expectedEventId,
      Object notificationEvent,
      String expectedFixtureNotificationId,
      String expectedFixtureMarkerId) {
    Object board = call(result, "boardToast");
    assertThat(value(board, "slot")).isEqualTo("toast");
    assertThat(value(board, "sourceSpec")).isEqualTo("S5");
    assertThat(value(board, "eventId")).isEqualTo(expectedEventId);
    assertThat(value(board, "type")).isEqualTo(expectedType);
    assertThat(value(board, "id"))
      .isEqualTo(value(notificationEvent, "id"))
        .isEqualTo(expectedFixtureNotificationId);
    assertThat(value(board, "markerId"))
      .isEqualTo(value(notificationEvent, "markerId"))
        .isEqualTo(expectedFixtureMarkerId);
    assertThat(value(board, "incidentId")).isEqualTo(value(notificationEvent, "incidentId"));
    assertThat(value(board, "opId")).isEqualTo(value(notificationEvent, "opId"));
    assertThat(value(board, "policePhoneId")).isEqualTo(value(notificationEvent, "policePhoneId"));
    assertThat(value(board, "status")).isEqualTo("SNAPSHOT_CREATED");
    assertThat(value(board, "version")).isEqualTo("1");
    assertThat(value(board, "boardResponseId")).isEqualTo("bs-inc-precinct-first-001");
    assertThat(booleanValue(board, "markerSlotConverged")).isTrue();
    assertThat(booleanValue(board, "toastSlotConverged")).isTrue();
    assertThat(booleanValue(board, "boardResponseVersionAtLeastNotificationVersion")).isTrue();
    assertThat(booleanValue(board, "staleBoardRejected")).isTrue();
    assertThat(booleanValue(board, "duplicateToastSuppressed")).isTrue();
    assertThat(value(board, "boardRefetchCalls")).isEqualTo("4");
    assertThat(value(board, "toastApplyLedgerStatus")).isEqualTo("APPLIED");
    assertThat(value(board, "duplicateToastLedgerStatus")).isEqualTo("DUPLICATE");
    assertThat(value(board, "staleToastLedgerStatus")).isEqualTo("STALE");
    assertThat(booleanValue(board, "converged")).isTrue();
  }

  private static Object run(String methodName) {
    Object runner = newRunner();
    return call(runner, methodName);
  }

  private static Object newRunner() {
    try {
      return Class.forName(RUNNER_CLASS).getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException exception) {
      fail("Missing SC-08 notification harness runner: " + RUNNER_CLASS, exception);
    } catch (ReflectiveOperationException exception) {
      fail("SC-08 notification harness runner must expose a no-arg constructor", exception);
    }
    throw new IllegalStateException("unreachable");
  }

  private static Object call(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException exception) {
      fail("Missing SC-08 harness evidence method: " + methodName, exception);
    } catch (IllegalAccessException exception) {
      fail("SC-08 harness evidence method must be public: " + methodName, exception);
    } catch (InvocationTargetException exception) {
      fail("SC-08 harness evidence method threw: " + methodName, exception.getCause());
    }
    throw new IllegalStateException("unreachable");
  }

  private static String value(Object target, String methodName) {
    Object value = call(target, methodName);
    return String.valueOf(value);
  }

  private static boolean booleanValue(Object target, String methodName) {
    Object value = call(target, methodName);
    assertThat(value).isInstanceOf(Boolean.class);
    return (Boolean) value;
  }

  private static List<String> listValue(Object target, String methodName) {
    Object value = call(target, methodName);
    assertThat(value).isInstanceOf(List.class);
    return ((List<?>) value).stream().map(String::valueOf).toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapValue(Object target, String methodName) {
    Object value = call(target, methodName);
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }
}
