package com.surimap.harness.sc04.fixture;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures;
import java.util.UUID;

/**
 * SC-04 harness 전용 fixture 상수 모음.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §harness_fixtures.sc04,
 * docs/spec/specs/S8.json §harness_fixtures.sc04,
 * docs/spec/harness-scenarios.md §2 SC-04.
 *
 * <p>모든 ID 값은 S2.json / S8.json harness_constraints 기준이며 BoundaryAreaFixtures 와
 * SearchAreaAssignmentFixtures 에 선언된 값을 재사용한다.
 */
public final class Sc04Fixtures {

  // ── incident / op ────────────────────────────────────────────────────────

  public static final String INCIDENT_ALIAS = BoundaryAreaFixtures.INCIDENT_ALIAS;
  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;

  public static final String OP1_ALIAS = BoundaryAreaFixtures.OP1_ALIAS;
  public static final UUID OP1_ID = BoundaryAreaFixtures.OP1_ID;

  // ── overall_search_area ──────────────────────────────────────────────────

  public static final String OVERALL_AREA_ALIAS = BoundaryAreaFixtures.OVERALL_AREA_ALIAS;
  public static final UUID OVERALL_AREA_ID = BoundaryAreaFixtures.OVERALL_AREA_ID;
  public static final long OVERALL_AREA_VERSION = BoundaryAreaFixtures.OVERALL_AREA_VERSION;

  public static final String OVERALL_AREA_EVENT_ID = BoundaryAreaFixtures.OVERALL_AREA_EVENT_ID;
  public static final long OVERALL_AREA_EVENT_SEQUENCE =
      BoundaryAreaFixtures.OVERALL_AREA_EVENT_SEQUENCE;

  // ── search_area (unit) ───────────────────────────────────────────────────

  public static final String AREA_ALIAS = BoundaryAreaFixtures.AREA_ALIAS;
  public static final UUID AREA_ID = BoundaryAreaFixtures.AREA_ID;
  public static final long AREA_CREATED_VERSION = BoundaryAreaFixtures.AREA_CREATED_VERSION;

  public static final String AREA_CREATED_EVENT_ID = BoundaryAreaFixtures.AREA_CREATED_EVENT_ID;
  public static final long AREA_CREATED_EVENT_SEQUENCE =
      BoundaryAreaFixtures.AREA_CREATED_EVENT_SEQUENCE;

  // ── search_area_assignment ───────────────────────────────────────────────

  public static final String ASSIGNMENT_ALIAS = SearchAreaAssignmentFixtures.ASSIGNMENT_ALIAS;
  public static final UUID ASSIGNMENT_ID = SearchAreaAssignmentFixtures.ASSIGNMENT_ID;
  public static final long ASSIGNMENT_VERSION = SearchAreaAssignmentFixtures.ASSIGNMENT_VERSION;

  public static final String ASSIGNMENT_CHANGED_EVENT_ID =
      SearchAreaAssignmentFixtures.ASSIGNMENT_CHANGED_EVENT_ID;
  public static final long ASSIGNMENT_CHANGED_EVENT_SEQUENCE =
      SearchAreaAssignmentFixtures.ASSIGNMENT_CHANGED_EVENT_SEQUENCE;

  public static final UUID ASSIGNEE_ACCOUNT_ID =
      SearchAreaAssignmentFixtures.ASSIGNEE_ACCOUNT_ID;
  public static final UUID ASSIGNED_BY_ACCOUNT_ID =
      SearchAreaAssignmentFixtures.ASSIGNED_BY_ACCOUNT_ID;

  // ── board convergence row IDs ────────────────────────────────────────────

  public static final String BOARD_OVERALL_SEARCH_AREA_ROW_ID =
      BoundaryAreaFixtures.BOARD_OVERALL_SEARCH_AREA_ROW_ID;
  public static final String BOARD_AREA_ROW_ID = BoundaryAreaFixtures.BOARD_AREA_ROW_ID;
  public static final String BOARD_ASSIGNMENT_ROW_ID =
      SearchAreaAssignmentFixtures.BOARD_ASSIGNMENT_ROW_ID;

  private Sc04Fixtures() {}
}
