package com.surimap.handover;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.handover.command.HandoverMemoCreateRequest;
import com.surimap.handover.command.HandoverMemoCreateResult;
import com.surimap.handover.fixture.HandoverMemoFixtures;
import com.surimap.handover.fixture.HandoverMemoFixtures.ExpectedHandoverMemoEvent;
import com.surimap.handover.testdouble.HandoverMemoCreateCommandMock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T07 HANDOVER_MEMO_CREATED PublishRequest contract RED test.
 *
 * <p>S8.json §api_contracts.events_published[HANDOVER_MEMO_CREATED] 기준.
 *
 * <p>harness fixture: sc11_handover_ai_convergence.expectedS4Events.handoverMemoCreated
 * - eventId: "evt-s8-handover-memo-001"
 * - type: "HANDOVER_MEMO_CREATED"
 * - payloadAlias: "memo-precinct-op2-001"
 * - payloadStatus: "ACTIVE"
 * - payloadVersion: 1
 * - opId: "op-precinct-001-op2"
 *
 * <p>S8.json events_published[HANDOVER_MEMO_CREATED].payload_schema 필수 필드:
 * id, incidentId, opId, status, version, targetType
 */
@DisplayName("L3-T07 HANDOVER_MEMO_CREATED PublishRequest contract")
class HandoverMemoCreatePublishRequestRedTest {

  private final HandoverMemoCreateCommandMock mock = new HandoverMemoCreateCommandMock();

  @Test
  @DisplayName("OP context 메모 생성은 HANDOVER_MEMO_CREATED 이벤트를 capture한다")
  void op_context_메모_생성은_handover_memo_created_이벤트를_capture한다() {
    HandoverMemoCreateRequest request =
        new HandoverMemoCreateRequest(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_OP,
            HandoverMemoFixtures.OP2_ID,
            "OP 인수인계 메모 내용",
            HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
            "WEB",
            HandoverMemoFixtures.CREATED_AT);

    mock.create(request);

    List<ExpectedHandoverMemoEvent> events = mock.publishedEvents();
    assertThat(events).hasSize(1);
    ExpectedHandoverMemoEvent event = events.get(0);

    // S8.json harness_fixtures.sc11_handover_ai_convergence.expectedS4Events.handoverMemoCreated
    assertThat(HandoverMemoFixtures.MEMO_ALIAS).isEqualTo("memo-precinct-op2-001");
    assertThat(event.type()).isEqualTo(HandoverMemoFixtures.EVENT_TYPE_HANDOVER_MEMO_CREATED);
    assertThat(event.eventId()).isEqualTo(HandoverMemoFixtures.MEMO_EVENT_ID);
    assertThat(event.payloadId()).isEqualTo(HandoverMemoFixtures.MEMO_ID);
    assertThat(event.payloadStatus()).isEqualTo(HandoverMemoFixtures.MEMO_STATUS);
    assertThat(event.payloadVersion()).isEqualTo(HandoverMemoFixtures.MEMO_VERSION);
    assertThat(event.opId()).isEqualTo(HandoverMemoFixtures.OP2_ID);
    assertThat(event.incidentId()).isEqualTo(HandoverMemoFixtures.INCIDENT_ID);
  }

  @Test
  @DisplayName("HANDOVER_MEMO_CREATED payload는 targetType 필드를 포함한다")
  void handover_memo_created_payload는_targetType_필드를_포함한다() {
    HandoverMemoCreateRequest request =
        new HandoverMemoCreateRequest(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_PATH,
            null,
            "경로 인수인계 메모",
            HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
            "APP",
            HandoverMemoFixtures.CREATED_AT);

    mock.create(request);

    ExpectedHandoverMemoEvent event = mock.publishedEvents().get(0);
    assertThat(event.targetType()).isEqualTo(HandoverMemoFixtures.TARGET_TYPE_PATH);
  }

  @Test
  @DisplayName("메모 생성 결과는 sc11 harness fixture의 id/opId/version을 포함한다")
  void 메모_생성_결과는_harness_fixture_값을_포함한다() {
    HandoverMemoCreateRequest request =
        new HandoverMemoCreateRequest(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_AREA,
            null,
            "구역 인수인계 메모",
            HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
            "WEB",
            HandoverMemoFixtures.CREATED_AT);

    HandoverMemoCreateResult result = mock.create(request);

    // S8.json harness_fixtures.sc11_handover_ai_convergence.expectedQueries.HandoverMemoQuery.byContext
    assertThat(result.id()).isEqualTo(HandoverMemoFixtures.MEMO_ID);
    assertThat(result.opId()).isEqualTo(HandoverMemoFixtures.OP2_ID);
    assertThat(result.version()).isEqualTo(HandoverMemoFixtures.MEMO_VERSION);
    assertThat(result.memoTargetType()).isEqualTo(HandoverMemoFixtures.TARGET_TYPE_AREA);
  }

  @Test
  @DisplayName("이벤트 publish 실패 시 row와 event capture가 모두 없다")
  void 이벤트_publish_실패시_row와_event_capture가_없다() {
    RuntimeException failure = new IllegalStateException("event_publish_failed");
    mock.failNextWith(failure);

    try {
      mock.create(
          new HandoverMemoCreateRequest(
              HandoverMemoFixtures.INCIDENT_ID,
              HandoverMemoFixtures.OP2_ID,
              HandoverMemoFixtures.TARGET_TYPE_OP,
              HandoverMemoFixtures.OP2_ID,
              "메모 내용",
              HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
              "WEB",
              HandoverMemoFixtures.CREATED_AT));
    } catch (RuntimeException ignored) {
      // expected
    }

    assertThat(mock.receivedRequests()).isEmpty();
    assertThat(mock.publishedEvents()).isEmpty();
  }
}
