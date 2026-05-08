package com.surimap.harness.sc02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.marker.notification.fixture.NotificationFixtures;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L5-T09D RED: SC-02 support assignment FCM evidence harness.
 *
 * <p>The harness runner is intentionally absent in RED. GREEN should implement only test-local
 * harness behavior that replays the 112/mock support assignment fanout, captures
 * MockFcmDispatcher evidence, and rejects missing FCM capture without creating marker_notification
 * rows.
 */
@DisplayName("SC-02 support assignment FCM harness RED")
class Sc02SupportAssignmentFcmHarnessRedTest {

  private static final String RUNNER_CLASS =
      "com.surimap.harness.sc02.Sc02SupportAssignmentFcmHarnessRunner";

  @Test
  @DisplayName("support assignment fanout captures only newly assigned support policePhone FCM")
  void supportAssignmentFanoutCapturesSupportFcmEvidence() {
    Object result = run("runSupportAssignmentFcmEvidence");

    assertThat(value(result, "scenarioId")).isEqualTo("SC-02");
    assertThat(value(result, "incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);

    Object event = call(result, "event");
    assertThat(value(event, "eventId")).isEqualTo(NotificationFixtures.ASSIGNMENT_EVENT_ID);
    assertThat(value(event, "type")).isEqualTo("INCIDENT_ASSIGNMENT_CHANGED");
    assertThat(value(event, "status")).isEqualTo("ACTIVE");
    assertThat(value(event, "version")).isEqualTo("2");
    assertThat(value(event, "sourceEntityType")).isEqualTo("incident_assignment");
    assertThat(listValue(event, "changedAccountIds"))
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_CHANGED_ACCOUNT_IDS);

    Object fcm = call(result, "fcm");
    assertThat(value(fcm, "dispatcher")).isEqualTo("MockFcmDispatcher");
    assertThat(value(fcm, "dispatchCount")).isEqualTo("1");
    assertThat(value(fcm, "capturedEventId")).isEqualTo(NotificationFixtures.ASSIGNMENT_EVENT_ID);
    assertThat(listValue(fcm, "recipients"))
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_FCM_RECIPIENTS);
    assertThat(listValue(fcm, "recipientAccountIds"))
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_RECIPIENT_ACCOUNT_IDS);
    assertThat(listValue(fcm, "recipientPolicePhoneIds"))
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_RECIPIENT_POLICE_PHONE_IDS);
    assertThat(listValue(fcm, "excludedPolicePhoneIds"))
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_EXCLUDED_POLICE_PHONE_IDS);
    assertThat(listValue(fcm, "recipients"))
        .doesNotContain("fcm:dev-support-cmd-phone-01");
    assertThat(booleanValue(fcm, "commandPolicePhoneExcluded")).isTrue();

    Object payload = call(result, "payload");
    assertThat(value(payload, "type")).isEqualTo("INCIDENT_ASSIGNMENT_CHANGED");
    assertThat(value(payload, "incidentId")).isEqualTo(NotificationFixtures.INCIDENT_ID);
    assertThat(value(payload, "status")).isEqualTo("ACTIVE");
    assertThat(value(payload, "version")).isEqualTo("2");
    assertThat(value(payload, "recipientPolicy"))
        .isEqualTo(NotificationFixtures.ASSIGNMENT_RECIPIENT_POLICY);
    assertThat(booleanValue(payload, "pii")).isFalse();
    assertThat(mapValue(payload, "data"))
        .containsEntry("type", "INCIDENT_ASSIGNMENT_CHANGED")
        .containsEntry("incidentId", NotificationFixtures.INCIDENT_ID)
        .containsEntry("status", "ACTIVE")
        .containsEntry("version", 2)
        .containsEntry("pii", false)
        .doesNotContainKeys(
            "missingPersonName",
            "missingPersonPhone",
            "residentRegistrationNumber",
            "guardianName",
            "guardianPhone",
            "address",
            "photoUrl");

    Object markerNotification = call(result, "markerNotification");
    assertThat(value(markerNotification, "table")).isEqualTo("marker_notification");
    assertThat(value(markerNotification, "rowsBefore")).isEqualTo("0");
    assertThat(value(markerNotification, "rowsAfter")).isEqualTo("0");
    assertThat(booleanValue(markerNotification, "created")).isFalse();
  }

  @Test
  @DisplayName("mock FCM missing failure injection rejects assignment evidence")
  void mockFcmMissingFailureInjectionFails() {
    assertThatThrownBy(() -> runAllowingRunnerException("runMockFcmMissingFailureInjection"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fcmMockMissing")
        .hasMessageContaining(NotificationFixtures.ASSIGNMENT_EVENT_ID)
        .hasMessageContaining("INCIDENT_ASSIGNMENT_CHANGED");
  }

  private static Object run(String methodName) {
    Object runner = newRunner();
    return call(runner, methodName);
  }

  private static Object runAllowingRunnerException(String methodName) {
    Object runner = newRunner();
    return callAllowingRunnerException(runner, methodName);
  }

  private static Object newRunner() {
    try {
      return Class.forName(RUNNER_CLASS).getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException exception) {
      fail("Missing SC-02 support assignment FCM harness runner: " + RUNNER_CLASS, exception);
    } catch (ReflectiveOperationException exception) {
      fail("SC-02 support assignment FCM harness runner must expose a no-arg constructor", exception);
    }
    throw new IllegalStateException("unreachable");
  }

  private static Object call(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException exception) {
      fail("Missing SC-02 harness evidence method: " + methodName, exception);
    } catch (IllegalAccessException exception) {
      fail("SC-02 harness evidence method must be public: " + methodName, exception);
    } catch (InvocationTargetException exception) {
      fail("SC-02 harness evidence method threw: " + methodName, exception.getCause());
    }
    throw new IllegalStateException("unreachable");
  }

  private static Object callAllowingRunnerException(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException exception) {
      fail("Missing SC-02 harness failure injection method: " + methodName, exception);
    } catch (IllegalAccessException exception) {
      fail("SC-02 harness failure injection method must be public: " + methodName, exception);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("SC-02 harness failure injection threw checked exception", cause);
    }
    throw new AssertionError("SC-02 mock FCM missing failure injection must fail");
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
