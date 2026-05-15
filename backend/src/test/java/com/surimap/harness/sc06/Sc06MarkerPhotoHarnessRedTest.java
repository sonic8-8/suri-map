package com.surimap.harness.sc06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L5-T09A RED: SC-06 marker/photo harness runner contract.
 *
 * <p>The runner is intentionally absent in RED. GREEN should implement the test harness with
 * existing S5 marker/photo services, mock object storage, S6/S4 mocks, and S3-2 board convergence
 * probes without adding production behavior.
 */
@DisplayName("SC-06 marker/photo harness runner RED")
class Sc06MarkerPhotoHarnessRedTest {

  private static final String RUNNER_CLASS =
      "com.surimap.harness.sc06.Sc06MarkerPhotoHarnessRunner";

  @Test
  @DisplayName(
      "online marker + one photo attach flow emits event/outbox and converges board marker slot")
  void onlineMarkerPhotoFlowProducesConvergenceEvidence() {
    Object result = run("runOnlineMarkerWithOneAttachedPhoto");

    assertThat(value(result, "scenarioId")).isEqualTo("SC-06");
    assertThat(value(result, "incidentId")).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
    assertThat(value(result, "markerId")).isEqualTo("55555555-5555-5555-5555-555555550001");
    assertThat(value(result, "photoId")).isEqualTo("55555555-5555-5555-5555-555555550101");

    Object auth = call(result, "auth");
    assertThat(value(auth, "channel")).isEqualTo("APP");
    assertThat(value(auth, "accountId")).isEqualTo("11111111-1111-1111-1111-111111110003");
    assertThat(value(auth, "policePhoneId")).isEqualTo("00000000-0000-0000-0000-000000000101");
    assertThat(booleanValue(auth, "registered")).isTrue();
    assertThat(booleanValue(auth, "assignedToIncident")).isTrue();
    assertThat(booleanValue(auth, "guardChecked")).isTrue();

    Object geometry = call(result, "geometry");
    assertThat(value(geometry, "type")).isEqualTo("Point");
    assertThat(listValue(geometry, "coordinates")).containsExactly("126.913400", "35.163100");
    assertThat(value(geometry, "srid")).isEqualTo("EPSG:4326");
    assertThat(booleanValue(geometry, "insideCurrentOverallSearchArea")).isTrue();

    Object currentOp = call(result, "currentOp");
    assertThat(value(currentOp, "opId")).isEqualTo("88888888-8888-8888-8888-888888880001");
    assertThat(value(currentOp, "queryCount")).isEqualTo("3");
    assertThat(booleanValue(currentOp, "matchedRequestOp")).isTrue();

    Object storage = call(result, "storage");
    assertThat(value(storage, "storageUri")).isEqualTo("mock://object-storage/suri-map-harness");
    assertThat(value(storage, "uploadUrl")).startsWith("http://127.0.0.1:18080/mock-upload/");
    assertThat(value(storage, "objectKey")).contains(value(storage, "internalPhotoUuid"));
    assertThat(value(storage, "internalPhotoUuid"))
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(booleanValue(storage, "externalS3Called")).isFalse();
    assertThat(booleanValue(storage, "attachedRowObserved")).isTrue();

    Object markerEvent = call(result, "markerCreatedEvent");
    assertThat(value(markerEvent, "type")).isEqualTo("MARKER_CREATED");
    assertThat(value(markerEvent, "status")).isEqualTo("ACTIVE");
    assertThat(value(markerEvent, "version")).isEqualTo("1");
    assertThat(value(markerEvent, "opId")).isEqualTo("88888888-8888-8888-8888-888888880001");
    assertThat(value(markerEvent, "policePhoneId")).isEqualTo("00000000-0000-0000-0000-000000000101");
    assertThat(value(markerEvent, "capturedPublishCount")).isEqualTo("1");
    assertThat(booleanValue(markerEvent, "eventDispatchJobCaptured")).isTrue();
    assertThat(value(markerEvent, "markerRowStatus")).isEqualTo("ACTIVE");
    assertThat(value(markerEvent, "markerRowCount")).isEqualTo("1");

    Object photoEvent = call(result, "photoAttachedEvent");
    assertThat(value(photoEvent, "eventId")).isEqualTo("evt-s5-marker-updated-photo-001");
    assertThat(value(photoEvent, "type")).isEqualTo("MARKER_UPDATED");
    assertThat(value(photoEvent, "status")).isEqualTo("UPDATED");
    assertThat(value(photoEvent, "version")).isEqualTo("2");
    assertThat(value(photoEvent, "photoId")).isEqualTo("55555555-5555-5555-5555-555555550101");
    assertThat(value(photoEvent, "internalPhotoUuid"))
        .isEqualTo(value(storage, "internalPhotoUuid"));
    assertThat(value(photoEvent, "photoStatus")).isEqualTo("ATTACHED");
    assertThat(value(photoEvent, "photoVersion")).isEqualTo("2");
    assertThat(value(photoEvent, "photoRowStatus")).isEqualTo("ATTACHED");
    assertThat(value(photoEvent, "photoRowVersion")).isEqualTo("2");
    assertThat(value(photoEvent, "capturedPublishCount")).isEqualTo("1");
    assertThat(value(photoEvent, "photoRowCount")).isEqualTo("1");

    Object outbox = call(result, "eventDispatchJob");
    assertThat(value(outbox, "eventId")).isEqualTo("evt-s5-marker-updated-photo-001");
    assertThat(value(outbox, "entityId")).isEqualTo("55555555-5555-5555-5555-555555550001");
    assertThat(value(outbox, "status")).isEqualTo("UPDATED");
    assertThat(value(outbox, "version")).isEqualTo("2");
    assertThat(value(outbox, "type")).isEqualTo("MARKER_UPDATED");
    assertThat(value(outbox, "photoDeltaStatus")).isEqualTo("ATTACHED");
    assertThat(value(outbox, "capturedJobCount")).isEqualTo("2");
    assertThat(booleanValue(outbox, "storedAfterCommit")).isTrue();

    Object sse = call(result, "sse");
    assertThat(value(sse, "eventId")).isEqualTo("evt-s5-marker-updated-photo-001");
    assertThat(value(sse, "entityId")).isEqualTo("55555555-5555-5555-5555-555555550001");
    assertThat(value(sse, "version")).isEqualTo("2");
    assertThat(value(sse, "status")).isEqualTo("UPDATED");
    assertThat(value(sse, "capturedMessageCount")).isEqualTo("2");

    Object board = call(result, "board");
    assertThat(value(board, "slot")).isEqualTo("marker");
    assertThat(value(board, "latestEventId")).isEqualTo("evt-s5-marker-updated-photo-001");
    assertThat(value(board, "markerId")).isEqualTo("55555555-5555-5555-5555-555555550001");
    assertThat(value(board, "photoId")).isEqualTo("55555555-5555-5555-5555-555555550101");
    assertThat(value(board, "internalPhotoUuid")).isEqualTo(value(photoEvent, "internalPhotoUuid"));
    assertThat(value(board, "photoStatus")).isEqualTo("ATTACHED");
    assertThat(value(board, "photoVersion")).isEqualTo("2");
    assertThat(value(board, "markerRowCount")).isEqualTo("1");
    assertThat(value(board, "boardResponseId")).isEqualTo("bs-inc-precinct-first-001");
    assertThat(value(board, "boardResponseVersion")).isEqualTo("2");
    assertThat(value(board, "sourceSpec")).isEqualTo("S5");
    assertThat(value(board, "sourceHash")).contains("S5:marker:mk-precinct-clue-001:v2");
    assertThat(value(board, "slotSourceCount")).isEqualTo("1");
    assertThat(value(board, "sourceVersionsCount")).isEqualTo("1");
    assertThat(value(board, "sourceHashesCount")).isEqualTo("1");
    assertThat(value(board, "refetchCalls")).isEqualTo("2");
    assertThat(booleanValue(board, "staleRefetchRejected")).isTrue();
    assertThat(booleanValue(board, "converged")).isTrue();

    Object secondResult = run("runOnlineMarkerWithOneAttachedPhoto");
    Object secondStorage = call(secondResult, "storage");
    assertThat(value(secondStorage, "internalPhotoUuid"))
        .isNotEqualTo(value(storage, "internalPhotoUuid"));
  }

  @Test
  @DisplayName("invalid marker Point keeps rows, outbox, event, and board response unchanged")
  void invalidMarkerGeometryIsRejectedBeforeSideEffects() {
    Object result = run("runInvalidGeometryRejection");

    Object geometry = call(result, "geometry");
    assertThat(value(geometry, "fixture")).isEqualTo("coord-outside-envelope");
    assertThat(value(geometry, "coordinates")).isEqualTo("[127.200000,35.163100]");

    Object rejection = call(result, "rejection");
    assertThat(value(rejection, "httpStatus")).isEqualTo("400");
    assertThat(value(rejection, "error")).isEqualTo("invalid_geometry");
    assertThat(value(rejection, "markerRowsBefore")).isEqualTo("0");
    assertThat(value(rejection, "markerRowsAfter")).isEqualTo("0");
    assertThat(value(rejection, "photoRowsBefore")).isEqualTo("0");
    assertThat(value(rejection, "photoRowsAfter")).isEqualTo("0");
    assertThat(value(rejection, "eventDispatchJobsBefore")).isEqualTo("0");
    assertThat(value(rejection, "eventDispatchJobsAfter")).isEqualTo("0");
    assertThat(value(rejection, "boardRowsBefore")).isEqualTo("0");
    assertThat(value(rejection, "boardRowsAfter")).isEqualTo("0");
    assertThat(booleanValue(rejection, "markerRowCreated")).isFalse();
    assertThat(booleanValue(rejection, "photoRowCreated")).isFalse();
    assertThat(booleanValue(rejection, "eventDispatchJobCreated")).isFalse();
    assertThat(booleanValue(rejection, "boardResponseChanged")).isFalse();
  }

  private static Object run(String methodName) {
    Object runner = newRunner();
    return call(runner, methodName);
  }

  private static Object newRunner() {
    try {
      return Class.forName(RUNNER_CLASS).getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException exception) {
      fail("Missing SC-06 marker/photo harness runner: " + RUNNER_CLASS, exception);
    } catch (ReflectiveOperationException exception) {
      fail("SC-06 marker/photo harness runner must expose a no-arg constructor", exception);
    }
    throw new IllegalStateException("unreachable");
  }

  private static Object call(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException exception) {
      fail("Missing SC-06 harness evidence method: " + methodName, exception);
    } catch (IllegalAccessException exception) {
      fail("SC-06 harness evidence method must be public: " + methodName, exception);
    } catch (InvocationTargetException exception) {
      fail("SC-06 harness evidence method threw: " + methodName, exception.getCause());
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
}
