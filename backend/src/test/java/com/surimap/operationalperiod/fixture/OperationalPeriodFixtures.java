package com.surimap.operationalperiod.fixture;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** S8 OP/current OP fixture IDs, 상태, 이벤트 모음. */
public final class OperationalPeriodFixtures {

  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;

  public static final String OP1_ALIAS = "op-precinct-001-op1";
  public static final UUID OP1_ID = BoundaryAreaFixtures.OP1_ID;
  public static final String OP2_ALIAS = "op-precinct-001-op2";
  public static final UUID OP2_ID = BoundaryAreaFixtures.OP2_ID;

  public static final Instant OP1_STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  /**
   * S8.json harness_fixtures.current_op_consumer_contract.forbiddenS8Writes 값.
   *
   * <p>source-of-truth에 search_path가 중복 기재되어 있어 exactness fixture도 그대로 보존한다.
   */
  public static final List<String> FORBIDDEN_CURRENT_OP_CONSUMER_WRITES =
      List.of(
          "offline_package_manifest",
          "offline_package_installation",
          "search_path",
          "search_path",
          "search_path_segment",
          "marker",
          "photo",
          "marker_notification");

  private OperationalPeriodFixtures() {}

  public static OperationalPeriodRow currentOp1() {
    return new OperationalPeriodRow(
        OP1_ID, INCIDENT_ID, "ACTIVE", 1, OP1_STARTED_AT, null, "INITIAL", 1L);
  }

  public static ExpectedOpTransitionEvent op1TransitionedEvent() {
    return new ExpectedOpTransitionEvent(
        "OP_TRANSITIONED", INCIDENT_ID, OP1_ID, OP1_ID, "ACTIVE", 1L, 1, null, OP1_ID);
  }

  public static RuntimeException op1CreationFailure() {
    return new IllegalStateException("op1_creation_failed");
  }

  /** OP2+ 전환 후 ENDED 상태가 된 OP1 row (S8.json §harness_fixtures.sc10_op_transition_convergence). */
  public static OperationalPeriodRow endedOp1() {
    return new OperationalPeriodRow(
        OP1_ID, INCIDENT_ID, "ENDED", 1, OP1_STARTED_AT, Instant.parse("2026-04-28T06:00:00Z"), "INITIAL", 2L);
  }

  /** OP2 ACTIVE row (S8.json §harness_fixtures.sc10_op_transition_convergence.newOp). */
  public static OperationalPeriodRow activeOp2(String reason) {
    return new OperationalPeriodRow(
        OP2_ID, INCIDENT_ID, "ACTIVE", 2, Instant.parse("2026-04-28T06:00:00Z"), null, reason, 1L);
  }

  /**
   * OP2+ 전환 이벤트 (S8.json §harness_fixtures.sc10_op_transition_convergence.expectedS4Event).
   *
   * <p>fromOpId=OP1, toOpId=OP2.
   */
  public static ExpectedOpTransitionEvent op2TransitionedEvent() {
    return new ExpectedOpTransitionEvent(
        "OP_TRANSITIONED", INCIDENT_ID, OP2_ID, OP2_ID, "ACTIVE", 1L, 2, OP1_ID, OP2_ID);
  }

  /**
   * S8 OP_TRANSITIONED 이벤트 payload를 테스트에서 비교하기 위한 읽기 모델.
   *
   * <p>S4 EventHub.publish PublishRequest의 id/status/version/opId 수렴 비교 기준.
   */
  public record ExpectedOpTransitionEvent(
      String type,
      UUID incidentId,
      UUID payloadId,
      UUID opId,
      String payloadStatus,
      long payloadVersion,
      int sequenceNumber,
      UUID fromOpId,
      UUID toOpId) {}
}
