package com.surimap.maparea.fixture;

import java.util.List;
import java.util.UUID;

/**
 * S2 overall_search_area/search_area fixture IDs, 상태, 이벤트 모음.
 *
 * <p>기준 문서: docs/spec/specs/S2.json harness_constraints.
 */
public final class BoundaryAreaFixtures {

  // TODO: S2 production enum이 생기면 상태, 이력 이벤트 타입, 발행 이벤트 타입 문자열을 enum 또는 wireValue() 기준으로 교체한다.

  /** 문서에 적힌 사람이 읽기 쉬운 SC-04 incident alias. 실제 DB ID는 UUID를 사용한다. */
  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";

  /** SC-04 시나리오에서 사용하는 고정 incident UUID. */
  public static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");

  /* --- SC-04 overall_search_area/search_area/이력 고정 ID (S2.json harness_constraints) --- */
  public static final String OVERALL_AREA_ALIAS = "osa-precinct-001-v1";
  public static final UUID OVERALL_AREA_ID =
      UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001");
  public static final String SUPERSEDED_OVERALL_AREA_ALIAS = "osa-precinct-001-v0";
  public static final UUID SUPERSEDED_OVERALL_AREA_ID =
      UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0000");
  public static final String AREA_ALIAS = "area-precinct-a1";
  public static final UUID AREA_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0001");
  public static final String OP1_ALIAS = "op-precinct-001-op1";
  public static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  public static final String OP2_ALIAS = "op-precinct-001-op2";
  public static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  public static final List<String> SPLIT_CHILD_AREA_ALIASES =
      List.of("area-precinct-a1-1", "area-precinct-a1-2");
  public static final List<UUID> SPLIT_CHILD_AREA_IDS =
      List.of(
          UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0011"),
          UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccc0012"));
  public static final String HISTORY_ALIAS = "area-hist-precinct-a1-001";
  public static final UUID HISTORY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddd0001");

  /* --- SC-04 이벤트/스냅샷 고정 ID와 version (S2.json harness_constraints) --- */
  public static final String OVERALL_AREA_EVENT_ID = "evt-s2-overall-area-001";
  public static final long OVERALL_AREA_EVENT_SEQUENCE = 401L;
  public static final long OVERALL_AREA_VERSION = 2L;

  public static final String AREA_CREATED_EVENT_ID = "evt-s2-area-created-001";
  public static final long AREA_CREATED_EVENT_SEQUENCE = 402L;
  public static final long AREA_CREATED_VERSION = 1L;

  public static final String AREA_STATE_EVENT_ID = "evt-s2-area-state-001";
  public static final long AREA_STATE_EVENT_SEQUENCE = 403L;
  public static final long AREA_STATE_VERSION = 3L;

  public static final String BOARD_OVERALL_SEARCH_AREA_ROW_ID =
      "board-overall-search-area-inc-precinct-first-001";
  public static final String BOARD_AREA_ROW_ID = "board-area-precinct-a1";

  /** search_area.status에 실제 저장되는 상태 enum (S2.json entity). */
  public static final List<String> SEARCH_AREA_STATUSES =
      List.of("ACTIVE", "COMPLETED", "CANCELLED");

  /** search_area_history.change_type에 남기는 이벤트 종류 (S2.json entity). */
  public static final List<String> SEARCH_AREA_HISTORY_CHANGE_TYPES =
      List.of("CREATED", "GEOMETRY_UPDATED", "STATUS_CHANGED", "SPLIT");

  /* --- split lifecycle (S2.json) --- */

  /** split 후 원본 구역(parent)은 CANCELLED 상태가 된다. */
  public static final String SPLIT_PARENT_NEXT_STATUS = "CANCELLED";

  /** split 후 새 구역(child)은 ACTIVE 상태로 시작한다. */
  public static final String SPLIT_CHILD_INITIAL_STATUS = "ACTIVE";

  public static final long SPLIT_CHILD_INITIAL_VERSION = 1L;

  private BoundaryAreaFixtures() {}

  /* --- Factory methods (record types for grouped data) --- */

  /** overall_search_area가 교체됐음을 알리는 SC-04 이벤트 envelope (SEARCH_AREA_CHANGED). */
  public static ExpectedEventEnvelope overallSearchAreaChangedEvent() {
    return new ExpectedEventEnvelope(
        OVERALL_AREA_EVENT_ID,
        "SEARCH_AREA_CHANGED",
        OVERALL_AREA_EVENT_SEQUENCE,
        OVERALL_AREA_ID,
        "ACTIVE",
        OVERALL_AREA_VERSION,
        null);
  }

  /** 수색 구역이 ACTIVE 상태로 생성됐음을 알리는 SC-04 이벤트 envelope (SEARCH_AREA_CHANGED). */
  public static ExpectedEventEnvelope areaCreatedEvent() {
    return new ExpectedEventEnvelope(
        AREA_CREATED_EVENT_ID,
        "SEARCH_AREA_CHANGED",
        AREA_CREATED_EVENT_SEQUENCE,
        AREA_ID,
        "ACTIVE",
        AREA_CREATED_VERSION,
        OP1_ID);
  }

  /** split 후 원본 수색 구역이 CANCELLED로 닫혔음을 알리는 SC-04 이벤트 envelope (SEARCH_AREA_CHANGED). */
  public static ExpectedEventEnvelope areaCancelledAfterSplitEvent() {
    return new ExpectedEventEnvelope(
        AREA_STATE_EVENT_ID,
        "SEARCH_AREA_CHANGED",
        AREA_STATE_EVENT_SEQUENCE,
        AREA_ID,
        "CANCELLED",
        AREA_STATE_VERSION,
        OP1_ID);
  }

  /**
   * S2 이벤트 발행 결과를 테스트에서 비교하기 위한 읽기 모델.
   *
   * <p>S4 EventHub.publish PublishRequest의 id/status/version 수렴 비교 기준.
   */
  public record ExpectedEventEnvelope(
      String eventId,
      String type,
      long sequence,
      UUID payloadId,
      String payloadStatus,
      long payloadVersion,
      UUID opId) {}
}
