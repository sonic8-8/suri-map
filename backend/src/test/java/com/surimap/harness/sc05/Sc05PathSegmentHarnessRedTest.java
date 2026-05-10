package com.surimap.harness.sc05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L4-T10A RED: SC-05 path/segment fixed fixture harness runner contract.
 *
 * <p>The runner is intentionally absent in RED. GREEN should implement only the test harness with
 * existing S3-1 path services/fixtures, S1-2 PolicePhone freshness fixtures, S2/S8 guard mocks, S4
 * event/SSE mocks, and S3-2 board convergence probes.
 */
@DisplayName("SC-05 path/segment fixture harness runner RED")
class Sc05PathSegmentHarnessRedTest {

  private static final String RUNNER_CLASS =
      "com.surimap.harness.sc05.Sc05PathSegmentHarnessRunner";

  @Test
  @DisplayName("normal mixed GPS path freezes path, segment, event, SSE, and board evidence")
  void normalMixedGpsPathProducesPathSegmentConvergenceEvidence() {
    Object result = run("runNormalMixedGpsPath");

    assertThat(value(result, "scenarioId")).isEqualTo("SC-05");
    assertThat(value(result, "incidentId")).isEqualTo("inc-precinct-first-001");
    assertThat(value(result, "opId")).isEqualTo("op-precinct-001-op1");
    assertThat(value(result, "policePhoneId")).isEqualTo("dev-precinct-car-01");
    assertThat(value(result, "pathId")).isEqualTo("path-precinct-mixed-001");

    Object auth = call(result, "auth");
    assertThat(value(auth, "channel")).isEqualTo("APP");
    assertThat(value(auth, "accountId")).isEqualTo("acct-precinct-car");
    assertThat(value(auth, "policePhoneId")).isEqualTo("dev-precinct-car-01");
    assertThat(booleanValue(auth, "registered")).isTrue();
    assertThat(booleanValue(auth, "assignedToIncident")).isTrue();
    assertThat(booleanValue(auth, "guardChecked")).isTrue();

    Object currentOp = call(result, "currentOp");
    assertThat(value(currentOp, "opId")).isEqualTo("op-precinct-001-op1");
    assertThat(booleanValue(currentOp, "matchedRequestOp")).isTrue();
    assertThat(booleanValue(currentOp, "guardChecked")).isTrue();

    Object geometry = call(result, "geometry");
    assertThat(value(geometry, "srid")).isEqualTo("EPSG:4326");
    assertThat(value(geometry, "minLon")).isEqualTo("126.900000");
    assertThat(value(geometry, "minLat")).isEqualTo("37.500000");
    assertThat(value(geometry, "maxLon")).isEqualTo("127.080000");
    assertThat(value(geometry, "maxLat")).isEqualTo("37.620000");
    assertThat(booleanValue(geometry, "allPointsInsideEnvelope")).isTrue();
    assertThat(booleanValue(geometry, "canonicalSixDecimalCoordinates")).isTrue();

    Object path = call(result, "path");
    assertThat(value(path, "fixtureId")).isEqualTo("gps-path-normal-001");
    assertThat(value(path, "apiPath")).isEqualTo("/api/search-paths/batch");
    assertThat(value(path, "httpMethod")).isEqualTo("POST");
    assertThat(value(path, "idempotencyKey")).isNotBlank();
    assertThat(value(path, "acceptedPointCount")).isEqualTo("8");
    assertThat(value(path, "excludedPointCount")).isEqualTo("0");
    assertThat(listValue(path, "pointIds"))
        .containsExactly(
            "gps-precinct-001",
            "gps-precinct-002",
            "gps-precinct-003",
            "gps-precinct-004",
            "gps-precinct-005",
            "gps-precinct-006",
            "gps-precinct-007",
            "gps-precinct-008");
    assertThat(listValue(path, "clientTsValues"))
        .containsExactly(
            "2026-04-28T09:00:00+09:00",
            "2026-04-28T09:00:05+09:00",
            "2026-04-28T09:00:10+09:00",
            "2026-04-28T09:00:15+09:00",
            "2026-04-28T09:00:20+09:00",
            "2026-04-28T09:00:25+09:00",
            "2026-04-28T09:00:30+09:00",
            "2026-04-28T09:00:35+09:00");

    Object segments = call(result, "segments");
    assertThat(listValue(segments, "segmentIds"))
        .containsExactly("seg-precinct-vehicle-001", "seg-precinct-foot-001");
    assertThat(listValue(segments, "movementTypes")).containsExactly("VEHICLE", "FOOT");
    assertThat(listValue(segments, "startIndexes")).containsExactly("0", "4");
    assertThat(listValue(segments, "endIndexes")).containsExactly("3", "7");
    assertThat(listValue(segments, "startPointIds"))
        .containsExactly("gps-precinct-001", "gps-precinct-005");
    assertThat(listValue(segments, "endPointIds"))
        .containsExactly("gps-precinct-004", "gps-precinct-008");

    Object startedEvent = call(result, "pathStartedEvent");
    assertThat(value(startedEvent, "eventId")).isEqualTo("evt-s3-path-started-001");
    assertThat(value(startedEvent, "type")).isEqualTo("SEARCH_PATH_STARTED");
    assertThat(value(startedEvent, "status")).isEqualTo("RECORDING");
    assertThat(value(startedEvent, "version")).isEqualTo("1");
    assertThat(value(startedEvent, "entityId")).isEqualTo("path-precinct-mixed-001");
    assertThat(value(startedEvent, "eventDispatchJobCount")).isEqualTo("1");

    Object appendedEvent = call(result, "pathAppendedEvent");
    assertThat(value(appendedEvent, "eventId")).isEqualTo("evt-s3-path-appended-001");
    assertThat(value(appendedEvent, "type")).isEqualTo("PATH_APPENDED");
    assertThat(value(appendedEvent, "status")).isEqualTo("RECORDING");
    assertThat(value(appendedEvent, "version")).isEqualTo("2");
    assertThat(value(appendedEvent, "entityId")).isEqualTo("path-precinct-mixed-001");
    assertThat(value(appendedEvent, "eventDispatchJobCount")).isEqualTo("1");

    Object segmentUpdatedEvent = call(result, "segmentUpdatedEvent");
    assertThat(value(segmentUpdatedEvent, "eventId")).isEqualTo("evt-s3-segment-updated-001");
    assertThat(value(segmentUpdatedEvent, "type")).isEqualTo("SEARCH_PATH_SEGMENT_UPDATED");
    assertThat(value(segmentUpdatedEvent, "status")).isEqualTo("RECORDING");
    assertThat(value(segmentUpdatedEvent, "version")).isEqualTo("3");
    assertThat(value(segmentUpdatedEvent, "entityId")).isEqualTo("path-precinct-mixed-001");
    assertThat(value(segmentUpdatedEvent, "segmentId")).isEqualTo("seg-precinct-vehicle-001");
    assertThat(value(segmentUpdatedEvent, "eventDispatchJobCount")).isEqualTo("1");

    Object sse = call(result, "sse");
    assertThat(listValue(sse, "eventIds"))
        .containsExactly(
            "evt-s3-path-started-001", "evt-s3-path-appended-001", "evt-s3-segment-updated-001");
    assertThat(listValue(sse, "versions")).containsExactly("1", "2", "3");
    assertThat(booleanValue(sse, "lastEventIdReplayVerified")).isTrue();
    assertThat(booleanValue(sse, "duplicateDeliverySuppressed")).isTrue();

    Object boardPath = call(result, "boardPath");
    assertThat(value(boardPath, "slot")).isEqualTo("path");
    assertThat(value(boardPath, "sourceSpec")).isEqualTo("S3-1");
    assertThat(value(boardPath, "sourceResponseId")).isEqualTo("path-precinct-mixed-001");
    assertThat(value(boardPath, "boardRowId")).isEqualTo("board-path-precinct-mixed-001");
    assertThat(value(boardPath, "boardResponseId")).isEqualTo("bs-inc-precinct-first-001");
    assertThat(value(boardPath, "latestEventId")).isEqualTo("evt-s3-segment-updated-001");
    assertThat(value(boardPath, "sourceVersion")).isEqualTo("3");
    assertThat(booleanValue(boardPath, "boardResponseVersionAtLeastSourceVersion")).isTrue();
    assertThat(booleanValue(boardPath, "staleRefetchRejected")).isTrue();
    assertThat(booleanValue(boardPath, "converged")).isTrue();
  }

  @Test
  @DisplayName("GPS quality failures are excluded and never promoted to saved path rows")
  void gpsQualityFixturesStayExcludedAndVisibleAsLowQualityEvidence() {
    Object result = run("runGpsQualityFixtureRejection");

    assertThat(value(result, "scenarioId")).isEqualTo("SC-05");
    Object quality = call(result, "quality");
    assertThat(listValue(quality, "fixtureNames"))
        .containsExactly(
            "gps-low-quality-accuracy-001",
            "gps-low-quality-skew-001",
            "gps-low-quality-speed-negative-001",
            "gps-low-quality-speed-over-001",
            "gps-low-quality-jump-001",
            "point-null-nan");
    assertThat(listValue(quality, "reasons"))
        .containsExactly(
            "LOW_ACCURACY",
            "CLOCK_SKEW",
            "INVALID_SPEED",
            "INVALID_SPEED",
            "DISTANCE_JUMP",
            "INVALID_GEOMETRY");
    assertThat(value(quality, "publicStructuralError")).isEqualTo("invalid_geometry");
    assertThat(booleanValue(quality, "noQualityPointPromotedToAcceptedPath")).isTrue();
    assertThat(booleanValue(quality, "appShowsLowQualityOrExcludedState")).isTrue();
    assertThat(booleanValue(quality, "boardShowsLowQualityOrExcludedState")).isTrue();
    assertThat(value(quality, "eventDispatchJobCount")).isEqualTo("5");
    assertThat(value(quality, "sseMessageCount")).isEqualTo("5");
    assertThat(booleanValue(quality, "boardPathRowUnchanged")).isTrue();
  }

  @Test
  @DisplayName("PolicePhone freshness DTO fields and derived statuses converge to board slot")
  void policePhoneFreshnessDtoConvergesToBoardFreshnessRows() {
    Object result = run("runPolicePhoneFreshnessConvergence");

    assertThat(value(result, "scenarioId")).isEqualTo("SC-05");

    Object query = call(result, "freshnessQuery");
    assertThat(value(query, "queryName")).isEqualTo("PolicePhoneFreshnessQuery.byIncident");
    assertThat(value(query, "incidentId")).isEqualTo("inc-precinct-first-001");
    assertThat(value(query, "policePhoneId")).isEqualTo("dev-precinct-car-01");
    assertThat(value(query, "accountId")).isEqualTo("acct-precinct-car");
    assertThat(value(query, "accountType")).isEqualTo("PATROL_CAR");
    assertThat(value(query, "organizationType")).isEqualTo("POLICE_SUBSTATION");
    assertThat(value(query, "opId")).isEqualTo("op-precinct-001-op1");
    assertThat(value(query, "lastHeartbeatAt")).isEqualTo("2026-04-28T10:29:40+09:00");
    assertThat(value(query, "lastSyncAt")).isEqualTo("2026-04-28T10:29:35+09:00");
    assertThat(listValue(query, "derivedFreshnessByElapsed"))
        .containsExactly("59s=ONLINE", "60s=STALE", "299s=STALE", "300s=LOST");
    assertThat(booleanValue(query, "derivedWithoutHeartbeatEvent")).isTrue();

    Object board = call(result, "boardFreshness");
    assertThat(value(board, "slot")).isEqualTo("police_phone_freshness");
    assertThat(value(board, "sourceSpec")).isEqualTo("S1-2");
    assertThat(value(board, "sourceResponseId")).isEqualTo("dev-precinct-car-01");
    assertThat(value(board, "boardRowId"))
        .isEqualTo("board-PolicePhone-freshness-dev-precinct-car-01");
    assertThat(value(board, "boardResponseId")).isEqualTo("bs-inc-precinct-first-001");
    assertThat(listValue(board, "statusesByElapsed"))
        .containsExactly("59s=ONLINE", "60s=STALE", "299s=STALE", "300s=LOST");
    assertThat(booleanValue(board, "lastHeartbeatAtMatchesQuery")).isTrue();
    assertThat(booleanValue(board, "lastSyncAtMatchesQuery")).isTrue();
    assertThat(booleanValue(board, "boardResponseVersionAtLeastSourceVersion")).isTrue();
    assertThat(booleanValue(board, "converged")).isTrue();
  }

  @Test
  @DisplayName("auth, geometry, OP, event, and stale board mocks expose negative contracts")
  void mockContractsExposeNegativePathHarnessEvidence() {
    Object result = run("runMockContractFailureInjection");

    Object auth = call(result, "authFailure");
    assertThat(value(auth, "policePhoneId")).isEqualTo("dev-unregistered-001");
    assertThat(value(auth, "httpStatus")).isEqualTo("403");
    assertThat(value(auth, "error")).isEqualTo("police_phone_not_registered");
    assertThat(booleanValue(auth, "pathRowCreated")).isFalse();
    assertThat(booleanValue(auth, "eventDispatchJobCreated")).isFalse();

    Object geometry = call(result, "geometryFailure");
    assertThat(listValue(geometry, "fixtures"))
        .containsExactly("coord-outside-envelope", "coord-latlon-swapped", "precision-over-6dp");
    assertThat(value(geometry, "httpStatus")).isEqualTo("400");
    assertThat(value(geometry, "error")).isEqualTo("invalid_geometry");
    assertThat(booleanValue(geometry, "pathRowCreated")).isFalse();
    assertThat(booleanValue(geometry, "boardResponseChanged")).isFalse();

    Object op = call(result, "opFailure");
    assertThat(value(op, "requestOpId")).isEqualTo("op-precinct-001-op2");
    assertThat(value(op, "currentOpId")).isEqualTo("op-precinct-001-op1");
    assertThat(value(op, "httpStatus")).isEqualTo("409");
    assertThat(value(op, "error")).isEqualTo("op_mismatch");
    assertThat(booleanValue(op, "pathRowCreated")).isFalse();

    Object event = call(result, "eventMockFailure");
    assertThat(value(event, "kind")).isEqualTo("EVENT_DISPATCH_JOB_MISSING");
    assertThat(value(event, "eventId")).isEqualTo("evt-s3-path-appended-001");
    assertThat(booleanValue(event, "restDbCommitted")).isTrue();
    assertThat(booleanValue(event, "eventDispatchJobCaptured")).isFalse();
    assertThat(booleanValue(event, "ssePayloadCaptured")).isFalse();
    assertThat(booleanValue(event, "boardConvergenceFailed")).isTrue();

    Object staleBoard = call(result, "staleBoardFailure");
    assertThat(value(staleBoard, "slot")).isEqualTo("path");
    assertThat(value(staleBoard, "sourceResponseId")).isEqualTo("path-precinct-mixed-001");
    assertThat(value(staleBoard, "staleVersion")).isEqualTo("1");
    assertThat(value(staleBoard, "sourceVersion")).isEqualTo("2");
    assertThat(value(staleBoard, "uiState")).isEqualTo("STALE_REFETCH");
    assertThat(booleanValue(staleBoard, "converged")).isFalse();
  }

  private static Object run(String methodName) {
    Object runner = newRunner();
    return call(runner, methodName);
  }

  private static Object newRunner() {
    try {
      return Class.forName(RUNNER_CLASS).getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException exception) {
      fail("Missing SC-05 path/segment harness runner: " + RUNNER_CLASS, exception);
    } catch (ReflectiveOperationException exception) {
      fail("SC-05 path/segment harness runner must expose a no-arg constructor", exception);
    }
    throw new IllegalStateException("unreachable");
  }

  private static Object call(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException exception) {
      fail("Missing SC-05 harness evidence method: " + methodName, exception);
    } catch (IllegalAccessException exception) {
      fail("SC-05 harness evidence method must be public: " + methodName, exception);
    } catch (InvocationTargetException exception) {
      fail("SC-05 harness evidence method threw: " + methodName, exception.getCause());
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
