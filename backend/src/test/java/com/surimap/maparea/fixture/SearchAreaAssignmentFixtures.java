package com.surimap.maparea.fixture;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * S2 search_area_assignment fixture IDs, 상태, 이벤트 모음.
 *
 * <p>기준 문서: docs/spec/specs/S2.json harness_constraints.
 * docs/spec/harness-scenarios.md §2 SC-04, SC-10.
 */
public final class SearchAreaAssignmentFixtures {

  // ── SC-04 assignment fixture IDs ────────────────────────────────────────

  /** SC-04 배정 assignment alias (harness-scenarios.md §6 mock 112 배정 사건). */
  public static final String ASSIGNMENT_ALIAS = "saa-precinct-a1-001";

  public static final UUID ASSIGNMENT_ID =
      UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeee0001");

  /** 배정된 계정 alias (acct-precinct-cmd). */
  public static final String ASSIGNEE_ACCOUNT_ALIAS = "acct-precinct-cmd";

  public static final UUID ASSIGNEE_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110001");

  /** 배정을 수행한 계정 alias. */
  public static final String ASSIGNED_BY_ACCOUNT_ALIAS = "acct-precinct-cmd";

  public static final UUID ASSIGNED_BY_ACCOUNT_ID = ASSIGNEE_ACCOUNT_ID;

  // ── SC-04 이벤트/스냅샷 고정 ID와 version ──────────────────────────────

  public static final String ASSIGNMENT_CHANGED_EVENT_ID = "evt-s2-assignment-001";
  public static final long ASSIGNMENT_CHANGED_EVENT_SEQUENCE = 410L;
  public static final long ASSIGNMENT_VERSION = 1L;
  public static final String BOARD_ASSIGNMENT_ROW_ID = "board-assignment-precinct-a1-001";

  // ── search_area_assignment.status ────────────────────────────────────────

  /** search_area_assignment.status에 실제 저장되는 상태 enum (S2.json entity). */
  public static final List<String> ASSIGNMENT_STATUSES = List.of("ACTIVE", "CANCELLED");

  private SearchAreaAssignmentFixtures() {}

  // ── factory methods ──────────────────────────────────────────────────────

  /**
   * SC-04 구역 배정 성공 시 발행되는 SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트 envelope.
   *
   * <p>S4 EventHub.publish PublishRequest의 id/status/version/opId 수렴 비교 기준.
   */
  public static ExpectedAssignmentEvent assignmentChangedEvent() {
    return new ExpectedAssignmentEvent(
        ASSIGNMENT_CHANGED_EVENT_ID,
        "SEARCH_AREA_ASSIGNMENT_CHANGED",
        ASSIGNMENT_CHANGED_EVENT_SEQUENCE,
        ASSIGNMENT_ID,
        BoundaryAreaFixtures.INCIDENT_ID,
        BoundaryAreaFixtures.OP1_ID,
        BoundaryAreaFixtures.AREA_ID,
        List.of(ASSIGNEE_ACCOUNT_ID),
        "ACTIVE",
        ASSIGNMENT_VERSION);
  }

  /**
   * S2 SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트 발행 결과를 테스트에서 비교하기 위한 읽기 모델.
   *
   * <p>S2.json events_published SEARCH_AREA_ASSIGNMENT_CHANGED payload_schema.
   */
  public record ExpectedAssignmentEvent(
      String eventId,
      String type,
      long sequence,
      UUID payloadId,
      UUID incidentId,
      UUID opId,
      UUID searchAreaId,
      List<UUID> assignedAccountIds,
      String payloadStatus,
      long payloadVersion) {}

  /** SC-04 구역 배정 단건 읽기 모델. SearchAreaAssignmentQuery.byOp 반환 row. */
  public static AssignmentRow activeAssignment() {
    return new AssignmentRow(
        ASSIGNMENT_ID,
        BoundaryAreaFixtures.AREA_ID,
        ASSIGNEE_ACCOUNT_ID,
        ASSIGNED_BY_ACCOUNT_ID,
        Instant.parse("2026-04-28T01:00:00Z"),
        null,
        "ACTIVE",
        ASSIGNMENT_VERSION);
  }

  /**
   * search_area_assignment 단건 읽기 모델.
   *
   * <p>SearchAreaAssignmentQuery.byOp / byArea 응답 row (S2.json entity 기준).
   */
  public record AssignmentRow(
      UUID id,
      UUID searchAreaId,
      UUID assignedAccountId,
      UUID assignedByAccountId,
      Instant assignedAt,
      Instant revokedAt,
      String status,
      long version) {}
}
