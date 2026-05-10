package com.surimap.harness.sc09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.board.BoardAssemblyLagState;
import com.surimap.board.BoardRefetchLedgerStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L4-T10B RED: SC-07 offline capture and SC-09 recovery replay harness contract. */
@DisplayName("SC-07/SC-09 offline replay dedupe harness RED")
class Sc07Sc09OfflineReplayHarnessRedTest {

  private static final String RUNNER_CLASS =
      "com.surimap.harness.sc09.Sc07Sc09OfflineReplayHarnessRunner";

  @Test
  @DisplayName("offline SC-07 rows recover through SC-09 outbox flush and converge board slots")
  void offlineRowsRecoverThroughOutboxFlushAndConvergeBoardSlots() {
    Object evidence = run("runOfflineThenRecovery");

    assertThat(value(evidence, "scenarioId")).isEqualTo("SC-07+SC-09");
    assertThat(value(evidence, "incidentId")).isEqualTo("inc-precinct-first-001");

    Object offline = call(evidence, "offline");
    assertThat(value(offline, "networkScriptId")).isEqualTo("net-script-domain-write-001");
    assertThat(value(offline, "pathLocalStatus")).isEqualTo("PENDING_LOCAL");
    assertThat(value(offline, "markerLocalStatus")).isEqualTo("PENDING_LOCAL");
    assertThat(value(offline, "pathOutboxStatus")).isEqualTo("PENDING_SEND");
    assertThat(value(offline, "markerOutboxStatus")).isEqualTo("PENDING_SEND");
    assertThat(value(offline, "policePhoneConnectivity")).isEqualTo("OFFLINE");
    assertThat(value(offline, "serverPathRows")).isEqualTo("0");
    assertThat(value(offline, "serverMarkerRows")).isEqualTo("0");
    assertThat(value(offline, "boardRows")).isEqualTo("0");

    Object recovery = call(evidence, "recovery");
    assertThat(value(recovery, "networkScriptId")).isEqualTo("net-script-outbox-flush-001");
    assertThat(value(recovery, "pathOutboxStatus")).isEqualTo("ACKED");
    assertThat(value(recovery, "markerOutboxStatus")).isEqualTo("ACKED");
    assertThat(value(recovery, "pathLocalStatus")).isEqualTo("SYNCED");
    assertThat(value(recovery, "markerLocalStatus")).isEqualTo("SYNCED");
    assertThat(value(recovery, "policePhoneConnectivity")).isEqualTo("ONLINE");
    assertThat(listValue(recovery, "flushTransitions"))
        .containsExactly("PENDING_SEND", "SENDING", "ACKED");
    assertThat(value(recovery, "pathOwnerInvocations")).isEqualTo("1");
    assertThat(value(recovery, "markerOwnerInvocations")).isEqualTo("1");
    assertThat(value(recovery, "pathEventJobs")).isEqualTo("1");
    assertThat(value(recovery, "markerEventJobs")).isEqualTo("1");
    assertThat(value(recovery, "pathWriteResponseVersion")).isEqualTo("2");
    assertThat(value(recovery, "markerWriteResponseVersion")).isEqualTo("2");
    assertThat(value(recovery, "freshnessOwnerCalls")).isEqualTo("1");

    Object path = call(evidence, "pathSlot");
    assertThat(value(path, "slot")).isEqualTo("path");
    assertThat(value(path, "id")).isEqualTo("path-precinct-mixed-001");
    assertThat(value(path, "status")).isEqualTo("ACTIVE");
    assertThat(value(path, "expectedVersion")).isEqualTo("2");
    assertThat(value(path, "boardRowVersion")).isEqualTo("2");
    assertThat(booleanValue(path, "converged")).isTrue();

    Object marker = call(evidence, "markerSlot");
    assertThat(value(marker, "slot")).isEqualTo("marker");
    assertThat(value(marker, "id")).isEqualTo("mk-precinct-clue-001");
    assertThat(value(marker, "status")).isEqualTo("UPDATED");
    assertThat(value(marker, "expectedVersion")).isEqualTo("2");
    assertThat(value(marker, "boardRowVersion")).isEqualTo("2");
    assertThat(booleanValue(marker, "converged")).isTrue();

    Object freshness = call(evidence, "policePhoneFreshnessSlot");
    assertThat(value(freshness, "slot")).isEqualTo("police_phone_freshness");
    assertThat(value(freshness, "id")).isEqualTo("dev-precinct-phone-01");
    assertThat(value(freshness, "status")).isEqualTo("NORMAL");
    assertThat(value(freshness, "expectedVersion")).isEqualTo("4");
    assertThat(value(freshness, "boardRowVersion")).isEqualTo("4");
    assertThat(booleanValue(freshness, "converged")).isTrue();

    Object board = call(evidence, "boardRefetch");
    assertThat(value(board, "pathApplyStatus")).isEqualTo(BoardRefetchLedgerStatus.APPLIED.name());
    assertThat(value(board, "markerApplyStatus")).isEqualTo(BoardRefetchLedgerStatus.APPLIED.name());
    assertThat(value(board, "freshnessApplyStatus"))
        .isEqualTo(BoardRefetchLedgerStatus.APPLIED.name());
    assertThat(booleanValue(board, "pathConverged")).isTrue();
    assertThat(booleanValue(board, "markerConverged")).isTrue();
    assertThat(booleanValue(board, "freshnessConverged")).isTrue();
  }

  @Test
  @DisplayName("same idempotency key/bodyHash replay does not duplicate domain rows or event jobs")
  void duplicateReplayDoesNotDuplicateDomainRowsOrEventJobs() {
    Object evidence = run("runDuplicateReplay");

    assertThat(value(evidence, "pathIdempotencyKey")).isEqualTo("idem-path-001");
    assertThat(value(evidence, "markerIdempotencyKey")).isEqualTo("idem-marker-001");
    assertThat(value(evidence, "pathOwnerInvocations")).isEqualTo("1");
    assertThat(value(evidence, "markerOwnerInvocations")).isEqualTo("1");
    assertThat(value(evidence, "pathRows")).isEqualTo("1");
    assertThat(value(evidence, "markerRows")).isEqualTo("1");
    assertThat(value(evidence, "pathEventJobs")).isEqualTo("1");
    assertThat(value(evidence, "markerEventJobs")).isEqualTo("1");
    assertThat(booleanValue(evidence, "pathReplayServedFromCache")).isTrue();
    assertThat(booleanValue(evidence, "markerReplayServedFromCache")).isTrue();
  }

  @Test
  @DisplayName("board API refetch lag is exposed as STALE_REFETCH before convergence")
  void boardApiRefetchLagIsExposedBeforeConvergence() {
    Object evidence = run("runBoardRefetchLagCheck");

    assertThat(value(evidence, "uiState")).isEqualTo(BoardAssemblyLagState.STALE_REFETCH);
    assertThat(value(evidence, "slot")).isEqualTo("path");
    assertThat(value(evidence, "entityId")).isEqualTo("path-precinct-mixed-001");
    assertThat(value(evidence, "sourceResponseVersion")).isEqualTo("2");
    assertThat(value(evidence, "staleResponseVersion")).isEqualTo("1");
    assertThat(value(evidence, "reloadAssertion"))
        .isEqualTo("BoardDTO row version >= 2 and sequence >= 502");
  }

  @Test
  @DisplayName("mocked owner endpoint fixtures cover path, marker, and freshness recovery")
  void mockedOwnerEndpointFixturesCoverRequiredSlots() {
    Object evidence = run("runMockedOwnerEndpointFixtures");

    assertThat(listValue(evidence, "fixtureNames"))
        .containsExactly(
            "mock-path-owner-endpoint",
            "mock-marker-owner-endpoint",
            "mock-police-phone-freshness-owner-endpoint");
    assertThat(listValue(evidence, "endpoints"))
        .containsExactly(
            "POST /search-paths/batch",
            "POST /markers",
            "GET /incidents/{incidentId}/police-phone-freshness");
    assertThat(listValue(evidence, "slots"))
        .containsExactly("path", "marker", "police_phone_freshness");
    assertThat(booleanValue(evidence, "externalNetworkCalled")).isFalse();
    assertThat(booleanValue(evidence, "committedCacheMissingRecovered")).isTrue();
    assertThat(value(evidence, "recoveredEntityId")).isEqualTo("path-precinct-mixed-001");
    assertThat(listValue(evidence, "recoveredEntityIds"))
        .containsExactly(
            "path-precinct-mixed-001", "mk-precinct-clue-001", "dev-precinct-phone-01");
    assertThat(value(evidence, "recoveryPortCalls")).isEqualTo("2");
    assertThat(booleanValue(evidence, "pathRecovered")).isTrue();
    assertThat(booleanValue(evidence, "markerRecovered")).isTrue();
    assertThat(booleanValue(evidence, "freshnessFixtureQueried")).isTrue();
    assertThat(booleanValue(evidence, "convergenceVerified")).isTrue();
  }

  private static Object run(String methodName) {
    Object runner = newRunner();
    return call(runner, methodName);
  }

  private static Object newRunner() {
    try {
      return Class.forName(RUNNER_CLASS).getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException exception) {
      fail("Missing SC-07/SC-09 offline replay harness runner: " + RUNNER_CLASS, exception);
    } catch (ReflectiveOperationException exception) {
      fail("SC-07/SC-09 harness runner must expose a no-arg constructor", exception);
    }
    throw new IllegalStateException("unreachable");
  }

  private static Object call(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException exception) {
      fail("Missing SC-07/SC-09 harness evidence method: " + methodName, exception);
    } catch (IllegalAccessException exception) {
      fail("SC-07/SC-09 harness evidence method must be public: " + methodName, exception);
    } catch (InvocationTargetException exception) {
      fail("SC-07/SC-09 harness evidence method threw: " + methodName, exception.getCause());
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
