package com.surimap.handover.fixture;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import java.time.Instant;
import java.util.UUID;

/**
 * L3-T07 handover_memo fixture IDs, 상태, 이벤트 모음.
 *
 * <p>기준 문서: docs/spec/specs/S8.json harness_fixtures.sc11_handover_ai_convergence
 */
public final class HandoverMemoFixtures {

  /** S8.json harness_fixtures.sc11_handover_ai_convergence.incidentId */
  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";

  public static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;

  /** S8.json harness_fixtures.sc11_handover_ai_convergence.opId */
  public static final String OP2_ALIAS = "op-precinct-001-op2";

  public static final UUID OP2_ID = BoundaryAreaFixtures.OP2_ID;

  /** S8.json harness_fixtures.sc11_handover_ai_convergence.handoverMemo.memoId */
  public static final String MEMO_ALIAS = "memo-precinct-handover-001";

  public static final UUID MEMO_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeee0010");

  /** S8.json harness_fixtures.sc11_handover_ai_convergence.handoverMemo.version */
  public static final long MEMO_VERSION = 1L;

  /** S8.json harness_fixtures.sc11_handover_ai_convergence.handoverMemo.status */
  public static final String MEMO_STATUS = "ACTIVE";

  /** S8.json harness_fixtures.sc11_handover_ai_convergence.expectedS4Events.handoverMemoCreated.eventId */
  public static final String MEMO_EVENT_ID = "evt-s8-handover-memo-001";

  /** S8.json handover_memo.memo_target_type enum values */
  public static final String TARGET_TYPE_OP = "OPERATIONAL_PERIOD";

  public static final String TARGET_TYPE_PATH = "SEARCH_PATH";
  public static final String TARGET_TYPE_AREA = "SEARCH_AREA";
  public static final String TARGET_TYPE_DUTY_SHIFT = "DUTY_SHIFT";
  public static final String TARGET_TYPE_MARKER = "MARKER";

  /** HANDOVER_MEMO_CREATED event type (S8.json events_published) */
  public static final String EVENT_TYPE_HANDOVER_MEMO_CREATED = "HANDOVER_MEMO_CREATED";

  /** S6 outbox dependency group for handover memo (S6.json enum: SESSION|PATH|MARKER|PHOTO|PACKAGE_STATUS).
   *  MARKER is the closest analog for independently queueable field-write operations. */
  public static final String OUTBOX_DEPENDENCY_GROUP = "MARKER";

  public static final String OUTBOX_METHOD = "POST";
  public static final String OUTBOX_ENDPOINT = "/api/handover-memos";

  /** Created-by account: a field commander or team phone account */
  public static final UUID CREATED_BY_ACCOUNT_ID =
      UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

  public static final Instant CREATED_AT = Instant.parse("2026-04-28T10:00:00Z");

  private HandoverMemoFixtures() {}

  /**
   * S8.json §service_contracts HandoverMemoQuery.byContext 응답 row 기준.
   *
   * <p>memoId/incidentId/opId/targetType/status/version이 harness_fixtures 값과 일치해야 한다.
   */
  public static HandoverMemoRow memoRow(String targetType, UUID targetId) {
    return new HandoverMemoRow(
        MEMO_ID,
        INCIDENT_ID,
        OP2_ID,
        targetType,
        targetId,
        "OP 인수인계 메모 내용",
        CREATED_BY_ACCOUNT_ID,
        CREATED_AT,
        MEMO_VERSION);
  }

  /**
   * SC-11 HANDOVER_MEMO_CREATED expected S4 event (S8.json harness_fixtures).
   *
   * <p>payloadId=memo-precinct-op2-001, payloadStatus=ACTIVE, payloadVersion=1, opId=op-precinct-001-op2
   */
  public static ExpectedHandoverMemoEvent handoverMemoCreatedEvent(
      String targetType, UUID targetId) {
    return new ExpectedHandoverMemoEvent(
        MEMO_EVENT_ID,
        EVENT_TYPE_HANDOVER_MEMO_CREATED,
        MEMO_ID,
        INCIDENT_ID,
        OP2_ID,
        targetType,
        targetId,
        MEMO_STATUS,
        MEMO_VERSION);
  }

  /**
   * S6 outbox-compatible write operation fixture for offline handover memo.
   *
   * <p>S8.json §dependencies.spec_dependencies[S6].stub_strategy: "duplicate key replay and offline memo fixture"
   */
  public static OutboxWriteOperation outboxWriteOperation(String idempotencyKey) {
    return new OutboxWriteOperation(
        UUID.randomUUID(),
        INCIDENT_ID,
        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddd0001"), // deviceId
        OUTBOX_DEPENDENCY_GROUP,
        1L,                                                       // sequence
        OUTBOX_METHOD,
        OUTBOX_ENDPOINT,
        "{\"targetType\":\"OPERATIONAL_PERIOD\"}",               // payload
        "sha256-placeholder",                                     // bodyHash
        idempotencyKey,
        OP2_ID,
        MEMO_ID,
        "handover_memo",
        CREATED_AT,
        0L,                                                       // clockOffsetMs
        CREATED_AT);                                              // clockSyncedAt
  }

  /**
   * S8.json §service_contracts HandoverMemoQuery.byContext 응답 row shape.
   *
   * <p>rowShape: memoId, incidentId, opId, targetType, targetId, content, createdByAccountId,
   * createdAt, version
   */
  public record HandoverMemoRow(
      UUID memoId,
      UUID incidentId,
      UUID opId,
      String targetType,
      UUID targetId,
      String content,
      UUID createdByAccountId,
      Instant createdAt,
      long version) {}

  /**
   * S8 HANDOVER_MEMO_CREATED 이벤트 payload를 테스트에서 비교하기 위한 읽기 모델.
   *
   * <p>S4 EventHub.publish PublishRequest의 id/status/version/opId 수렴 비교 기준.
   *
   * <p>S8.json events_published[HANDOVER_MEMO_CREATED].payload_schema 필수 필드:
   * id, incidentId, opId, status, version, targetType
   */
  public record ExpectedHandoverMemoEvent(
      String eventId,
      String type,
      UUID payloadId,
      UUID incidentId,
      UUID opId,
      String targetType,
      UUID targetId,
      String payloadStatus,
      long payloadVersion) {}

  /**
   * S6 outbox write operation 최소 계약 shape.
   *
   * <p>S6.json §sync_contracts.write_operation_schema 필수 필드 중 handover memo에 해당하는 서브셋.
   */
  public record OutboxWriteOperation(
      UUID operationId,
      UUID incidentId,
      UUID deviceId,
      String dependencyGroup,
      long sequence,
      String method,
      String endpoint,
      String payload,
      String bodyHash,
      String idempotencyKey,
      UUID opId,
      UUID entityId,
      String entityType,
      Instant clientTs,
      long clockOffsetMs,
      Instant clockSyncedAt) {}
}
