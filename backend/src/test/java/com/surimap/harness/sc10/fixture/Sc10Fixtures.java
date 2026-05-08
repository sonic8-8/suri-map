package com.surimap.harness.sc10.fixture;

import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.handover.fixture.HandoverMemoFixtures;
import java.util.UUID;

/**
 * SC-10 harness 전용 fixture 상수 모음.
 *
 * <p>기준 문서: docs/spec/specs/S8.json §harness_fixtures.sc10_op_transition_convergence,
 * docs/spec/harness-scenarios.md §2 SC-10.
 *
 * <p>S8.json harness_constraints 기준이며 OperationalPeriodFixtures / HandoverMemoFixtures 값을 재사용한다.
 */
public final class Sc10Fixtures {

  // ── incident / op ────────────────────────────────────────────────────────

  public static final String INCIDENT_ALIAS = OperationalPeriodFixtures.INCIDENT_ALIAS;
  public static final UUID INCIDENT_ID = OperationalPeriodFixtures.INCIDENT_ID;

  public static final String OP1_ALIAS = OperationalPeriodFixtures.OP1_ALIAS;
  public static final UUID OP1_ID = OperationalPeriodFixtures.OP1_ID;

  public static final String OP2_ALIAS = OperationalPeriodFixtures.OP2_ALIAS;
  public static final UUID OP2_ID = OperationalPeriodFixtures.OP2_ID;

  // ── OP1 (ENDED after transition) ────────────────────────────────────────

  public static final String PREVIOUS_OP_STATUS = OperationalPeriodFixtures.PREVIOUS_OP_STATUS;
  public static final long PREVIOUS_OP_VERSION = OperationalPeriodFixtures.PREVIOUS_OP_VERSION;
  public static final int PREVIOUS_OP_SEQUENCE_NO = OperationalPeriodFixtures.PREVIOUS_OP_SEQUENCE_NO;

  // ── OP2 (ACTIVE after transition) ────────────────────────────────────────

  public static final String NEW_OP_STATUS = OperationalPeriodFixtures.NEW_OP_STATUS;
  public static final long NEW_OP_VERSION = OperationalPeriodFixtures.NEW_OP_VERSION;
  public static final int NEW_OP_SEQUENCE_NO = OperationalPeriodFixtures.NEW_OP_SEQUENCE_NO;
  public static final String NEW_OP_REASON = OperationalPeriodFixtures.NEW_OP_REASON;

  // ── OP_TRANSITIONED event ────────────────────────────────────────────────

  /** S8.json harness_fixtures.sc10 OP_TRANSITIONED eventId */
  public static final String OP_TRANSITIONED_EVENT_ID = "evt-s8-op-transitioned-001";

  // ── handover memo ────────────────────────────────────────────────────────

  public static final String MEMO_ALIAS = HandoverMemoFixtures.MEMO_ALIAS;
  public static final UUID MEMO_ID = HandoverMemoFixtures.MEMO_ID;
  public static final long MEMO_VERSION = HandoverMemoFixtures.MEMO_VERSION;
  public static final String MEMO_STATUS = HandoverMemoFixtures.MEMO_STATUS;
  public static final String MEMO_EVENT_ID = HandoverMemoFixtures.MEMO_EVENT_ID;

  // ── SC-10 harness_execution_log step names ───────────────────────────────

  public static final String STEP_RADIO_REPORT_RECEIVED = "radio_report_received";
  public static final String STEP_COMMANDER_DECISION_RECORDED = "commander_decision_recorded";
  public static final String STEP_AREA_COMPLETED = "area_completed";
  public static final String STEP_OP_TRANSITIONED = "op_transitioned";
  public static final String STEP_HANDOVER_SAVED = "handover_saved";
  public static final String STEP_BOARD_API_REFETCHED = "board_api_refetched";

  // ── board convergence slots ──────────────────────────────────────────────

  public static final String BOARD_OP_TOGGLE_SLOT = "op_toggle";
  public static final String BOARD_OP_HISTORY_SLOT = "op_history";
  public static final String BOARD_HANDOVER_STATUS_SLOT = "handover_status";

  private Sc10Fixtures() {}
}
