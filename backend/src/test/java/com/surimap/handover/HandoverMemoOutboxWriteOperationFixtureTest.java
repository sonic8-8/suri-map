package com.surimap.handover;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.handover.fixture.HandoverMemoFixtures;
import com.surimap.handover.fixture.HandoverMemoFixtures.OutboxWriteOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T07 S6 outbox-compatible handover memo write operation fixture RED test.
 *
 * <p>S8.json §dependencies.spec_dependencies[S6].stub_strategy:
 * "duplicate key replay and offline memo fixture"
 *
 * <p>S6.json §sync_contracts.write_operation_schema 필수 필드 검증:
 * operationId, incidentId, dependencyGroup, method, endpoint, idempotencyKey, opId, entityId,
 * entityType, clientTs
 *
 * <p>S8.json §scope.included:
 * "OP/경로/구역 단위 handover_memo 생성·조회"
 * "offline app memo는 S6 outbox를 사용" (완료 기준)
 */
@DisplayName("L3-T07 S6 outbox-compatible handover memo write operation fixture")
class HandoverMemoOutboxWriteOperationFixtureTest {

  private static final String IDEMPOTENCY_KEY = "idem-s8-handover-memo-offline-001";

  @Test
  @DisplayName("outbox write operation은 S6 schema 필수 필드를 포함한다")
  void outbox_write_operation은_s6_schema_필수_필드를_포함한다() {
    OutboxWriteOperation op = HandoverMemoFixtures.outboxWriteOperation(IDEMPOTENCY_KEY);

    // S6.json §sync_contracts.write_operation_schema required_fields (13개)
    assertThat(op.operationId()).isNotNull();
    assertThat(op.incidentId()).isEqualTo(HandoverMemoFixtures.INCIDENT_ID);
    assertThat(op.deviceId()).isNotNull();
    assertThat(op.dependencyGroup()).isEqualTo("MARKER"); // S6.json enum: SESSION|PATH|MARKER|PHOTO|PACKAGE_STATUS
    assertThat(op.sequence()).isGreaterThan(0L);
    assertThat(op.method()).isEqualTo("POST");
    assertThat(op.endpoint()).isEqualTo("/api/handover-memos");
    assertThat(op.payload()).isNotBlank();
    assertThat(op.bodyHash()).isNotBlank();
    assertThat(op.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    assertThat(op.clientTs()).isNotNull();
    assertThat(op.clockSyncedAt()).isNotNull();
  }

  @Test
  @DisplayName("outbox write operation endpoint는 POST /api/handover-memos이다")
  void outbox_endpoint는_handover_memos이다() {
    OutboxWriteOperation op = HandoverMemoFixtures.outboxWriteOperation(IDEMPOTENCY_KEY);

    assertThat(op.method()).isEqualTo("POST");
    assertThat(op.endpoint()).isEqualTo("/api/handover-memos");
  }

  @Test
  @DisplayName("outbox write operation은 opId와 entityId를 포함한다")
  void outbox_operation은_opId와_entityId를_포함한다() {
    OutboxWriteOperation op = HandoverMemoFixtures.outboxWriteOperation(IDEMPOTENCY_KEY);

    // S6.json §sync_contracts.write_operation_schema optional_fields: opId, entityId, entityType
    assertThat(op.opId()).isEqualTo(HandoverMemoFixtures.OP2_ID);
    assertThat(op.entityId()).isEqualTo(HandoverMemoFixtures.MEMO_ID);
    assertThat(op.entityType()).isEqualTo("handover_memo");
  }

  @Test
  @DisplayName("오프라인 메모 두 번 재전송은 같은 idempotencyKey를 사용한다")
  void 오프라인_메모_재전송은_같은_idempotencyKey를_사용한다() {
    OutboxWriteOperation first = HandoverMemoFixtures.outboxWriteOperation(IDEMPOTENCY_KEY);
    OutboxWriteOperation replay = HandoverMemoFixtures.outboxWriteOperation(IDEMPOTENCY_KEY);

    // 같은 idempotencyKey로 두 번 전송해도 replay는 같은 key를 가진다
    assertThat(first.idempotencyKey()).isEqualTo(replay.idempotencyKey());
    assertThat(first.incidentId()).isEqualTo(replay.incidentId());
    assertThat(first.endpoint()).isEqualTo(replay.endpoint());
  }

  @Test
  @DisplayName("outbox write operation incidentId는 sc11 harness fixture 값과 일치한다")
  void outbox_incidentId는_harness_fixture_값과_일치한다() {
    OutboxWriteOperation op = HandoverMemoFixtures.outboxWriteOperation(IDEMPOTENCY_KEY);

    // S8.json harness_fixtures.sc11_handover_ai_convergence.incidentId
    assertThat(op.incidentId()).isEqualTo(HandoverMemoFixtures.INCIDENT_ID);
  }
}
