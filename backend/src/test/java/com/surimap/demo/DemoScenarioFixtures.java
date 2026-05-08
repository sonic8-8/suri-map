package com.surimap.demo;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.handover.fixture.HandoverMemoFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 시연용 통합 demo fixture (SC-04, SC-10, SC-11).
 *
 * <p>기준 문서:
 * - prd.md §5.1
 * - docs/spec/harness-scenarios.md §2 SC-04, SC-10, SC-11
 * - docs/spec/harness-scenarios.md §6 mock 112 배정 사건
 *
 * <p>이 fixture는 live manual data repair에 의존하지 않는 self-contained demo 데이터다.
 * 모든 ID와 값은 harness-scenarios.md §6 mock 112 배정 사건 기준을 따른다.
 */
public final class DemoScenarioFixtures {

  // ── 사건 (incident) ──────────────────────────────────────────────────────

  /** mock 112 배정 사건 alias (harness-scenarios.md §6) */
  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";

  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;

  // ── 계정 (account) ──────────────────────────────────────────────────────

  /** 지구대/파출소 지휘 계정 */
  public static final String PRECINCT_CMD_ALIAS = "acct-precinct-cmd";

  public static final UUID PRECINCT_CMD_ACCOUNT_ID =
      SearchAreaAssignmentFixtures.ASSIGNEE_ACCOUNT_ID;

  // ── OP1: 초동 출동 ───────────────────────────────────────────────────────

  /** SC-04 시나리오 기준 OP1 (최초 활성 OP) */
  public static final String OP1_ALIAS = OperationalPeriodFixtures.OP1_ALIAS;

  public static final UUID OP1_ID = OperationalPeriodFixtures.OP1_ID;
  public static final String OP1_STATUS_ACTIVE = "ACTIVE";
  public static final String OP1_STATUS_ENDED = "ENDED";
  public static final String OP1_REASON = "INITIAL";
  public static final int OP1_SEQUENCE_NO = 1;
  public static final long OP1_VERSION_ACTIVE = 1L;
  public static final long OP1_VERSION_ENDED = OperationalPeriodFixtures.PREVIOUS_OP_VERSION;
  public static final Instant OP1_STARTED_AT = OperationalPeriodFixtures.OP1_STARTED_AT;
  public static final Instant OP1_ENDED_AT = OperationalPeriodFixtures.PREVIOUS_OP_ENDED_AT;

  // ── overall_search_area ──────────────────────────────────────────────────

  /** SC-04 Step-1: 전체 수색 구역 (overall_search_area) */
  public static final String OVERALL_AREA_ALIAS = BoundaryAreaFixtures.OVERALL_AREA_ALIAS;

  public static final UUID OVERALL_AREA_ID = BoundaryAreaFixtures.OVERALL_AREA_ID;
  public static final String OVERALL_AREA_STATUS = "ACTIVE";
  public static final long OVERALL_AREA_VERSION = BoundaryAreaFixtures.OVERALL_AREA_VERSION;
  public static final String OVERALL_AREA_EVENT_ID = BoundaryAreaFixtures.OVERALL_AREA_EVENT_ID;
  public static final long OVERALL_AREA_EVENT_SEQUENCE =
      BoundaryAreaFixtures.OVERALL_AREA_EVENT_SEQUENCE;

  // ── search_area (unit) ───────────────────────────────────────────────────

  /** SC-04 Step-2: 단위 수색 구역 */
  public static final String AREA_ALIAS = BoundaryAreaFixtures.AREA_ALIAS;

  public static final UUID AREA_ID = BoundaryAreaFixtures.AREA_ID;
  public static final String AREA_STATUS_ACTIVE = "ACTIVE";
  public static final String AREA_STATUS_COMPLETED = "COMPLETED";
  public static final long AREA_VERSION_CREATED = BoundaryAreaFixtures.AREA_CREATED_VERSION;
  public static final String AREA_CREATED_EVENT_ID = BoundaryAreaFixtures.AREA_CREATED_EVENT_ID;
  public static final long AREA_CREATED_EVENT_SEQUENCE =
      BoundaryAreaFixtures.AREA_CREATED_EVENT_SEQUENCE;

  // ── search_area_assignment ───────────────────────────────────────────────

  /** SC-04 Step-3: 담당 구역 배정 */
  public static final String ASSIGNMENT_ALIAS = SearchAreaAssignmentFixtures.ASSIGNMENT_ALIAS;

  public static final UUID ASSIGNMENT_ID = SearchAreaAssignmentFixtures.ASSIGNMENT_ID;
  public static final String ASSIGNMENT_STATUS = "ACTIVE";
  public static final long ASSIGNMENT_VERSION = SearchAreaAssignmentFixtures.ASSIGNMENT_VERSION;
  public static final String ASSIGNMENT_EVENT_ID =
      SearchAreaAssignmentFixtures.ASSIGNMENT_CHANGED_EVENT_ID;
  public static final long ASSIGNMENT_EVENT_SEQUENCE =
      SearchAreaAssignmentFixtures.ASSIGNMENT_CHANGED_EVENT_SEQUENCE;
  public static final List<UUID> ASSIGNEE_ACCOUNT_IDS =
      List.of(PRECINCT_CMD_ACCOUNT_ID);

  // ── OP2: SC-10 OP 전환 ───────────────────────────────────────────────────

  /** SC-10 Step-4: OP1 → OP2 전환 */
  public static final String OP2_ALIAS = OperationalPeriodFixtures.OP2_ALIAS;

  public static final UUID OP2_ID = OperationalPeriodFixtures.OP2_ID;
  public static final String OP2_STATUS = "ACTIVE";
  public static final String OP2_REASON = "RE_SEARCH";
  public static final int OP2_SEQUENCE_NO = 2;
  public static final long OP2_VERSION = OperationalPeriodFixtures.NEW_OP_VERSION;
  public static final Instant OP2_STARTED_AT = OperationalPeriodFixtures.NEW_OP_STARTED_AT;

  // ── handover memo (SC-10/SC-11) ──────────────────────────────────────────

  /** SC-10 Step-5 / SC-11 기준: 인수인계 메모 */
  public static final String HANDOVER_MEMO_ALIAS = HandoverMemoFixtures.MEMO_ALIAS;

  public static final UUID HANDOVER_MEMO_ID = HandoverMemoFixtures.MEMO_ID;
  public static final String HANDOVER_MEMO_STATUS = HandoverMemoFixtures.MEMO_STATUS;
  public static final long HANDOVER_MEMO_VERSION = HandoverMemoFixtures.MEMO_VERSION;
  public static final String HANDOVER_MEMO_EVENT_ID = HandoverMemoFixtures.MEMO_EVENT_ID;
  public static final String HANDOVER_MEMO_TARGET_TYPE = "OPERATIONAL_PERIOD";
  public static final String HANDOVER_MEMO_CONTENT =
      "OP1 수색 종료. 북쪽 구역 미완료 상태. 팀 인수인계 요망.";

  // ── search_history_summary OpenAI input fixtures (SC-11) ─────────────────

  /**
   * SC-11 OpenAI 성공 케이스 input evidence (minimized internal data only).
   *
   * <p>S8.json §FR-23: 추천·누락확정·위험도·자동판단 문구 금지. source prompt 노출 금지.
   */
  public static final String SUMMARY_EVIDENCE_SUCCESS =
      "OP1 기간 내 수색 경로 2건, 마커 1건, 메모 1건 기록됨. 담당 구역 area-precinct-a1 완료.";

  /**
   * SC-11 OpenAI 실패 케이스 input (forbidden phrase 포함 — FR-23 guard 트리거용).
   *
   * <p>이 evidence를 사용하면 ForbiddenSummaryGuard가 차단하여 generation_status=FAILED가 된다.
   */
  public static final String SUMMARY_EVIDENCE_WITH_FORBIDDEN_PHRASE =
      "수색 결과 다음 구역 추천: 북쪽. 위험도 높음.";

  // ── board convergence row IDs ────────────────────────────────────────────

  /** S2.json harness_constraints board row IDs */
  public static final String BOARD_OVERALL_SEARCH_AREA_ROW_ID =
      BoundaryAreaFixtures.BOARD_OVERALL_SEARCH_AREA_ROW_ID;

  public static final String BOARD_AREA_ROW_ID = BoundaryAreaFixtures.BOARD_AREA_ROW_ID;
  public static final String BOARD_ASSIGNMENT_ROW_ID =
      SearchAreaAssignmentFixtures.BOARD_ASSIGNMENT_ROW_ID;

  // ── SC-10 board convergence slots ────────────────────────────────────────

  public static final String BOARD_OP_TOGGLE_SLOT = "op_toggle";
  public static final String BOARD_OP_HISTORY_SLOT = "op_history";
  public static final String BOARD_HANDOVER_STATUS_SLOT = "handover_status";

  // ── manual data repair checklist ─────────────────────────────────────────

  /**
   * 시연 전 수동 데이터 검증 체크리스트.
   *
   * <p>live data repair 없이 fixture만으로 demo가 가능한지 확인하는 항목 목록이다.
   */
  public static final List<String> MANUAL_REPAIR_CHECKLIST = List.of(
      "INCIDENT_ID=" + INCIDENT_ALIAS + " 존재 확인",
      "OP1 (id=" + OP1_ALIAS + ") ACTIVE 상태 확인",
      "OVERALL_AREA (id=" + OVERALL_AREA_ALIAS + ") ACTIVE 상태 확인",
      "AREA (id=" + AREA_ALIAS + ") ACTIVE 상태 확인",
      "ASSIGNMENT (id=" + ASSIGNMENT_ALIAS + ") ACTIVE, assignee=" + PRECINCT_CMD_ALIAS,
      "OP2 전환 요청: reason=RE_SEARCH, reasonMemo=null",
      "HANDOVER_MEMO (id=" + HANDOVER_MEMO_ALIAS + ") OP2 소속 ACTIVE 확인",
      "SUMMARY evidence에 금지 문구(다음 구역 추천/누락 확정/위험도 높음/자동 판단) 미포함 확인"
  );

  // ── geometry (SC-04 기준) ────────────────────────────────────────────────

  /** SC-04 overall_search_area 경계 bbox [minLon, minLat, maxLon, maxLat] */
  public static final List<java.math.BigDecimal> OVERALL_AREA_BBOX =
      GeometryFixtures.BOUNDARY_GEOMETRY_BBOX;

  /** SC-04 search_area bbox [minLon, minLat, maxLon, maxLat] */
  public static final List<java.math.BigDecimal> AREA_BBOX = GeometryFixtures.AREA_GEOMETRY_BBOX;

  private DemoScenarioFixtures() {}
}
