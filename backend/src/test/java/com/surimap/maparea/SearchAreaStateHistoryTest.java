package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.GeometryFixtures;
import com.surimap.maparea.statemachine.SearchAreaStateTransitionService;
import com.surimap.maparea.statemachine.SearchAreaStateTransitionService.StateTransitionCommand;
import com.surimap.maparea.statemachine.SearchAreaStateTransitionService.StateTransitionResult;
import com.surimap.maparea.statemachine.SearchAreaHistory;
import com.surimap.maparea.event.PublishRequestCollector;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * L3-T03 테스트: 상태 전이, history 기록, SEARCH_AREA_CHANGED 이벤트 발행 계약.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §state_machines, §entity(search_area_history),
 * §events_published[2], docs/spec/boundaries.md §10 SC-10.
 */
class SearchAreaStateHistoryTest {

  /** 상황 지휘관 계정 ID — Phase 1 in-memory 테스트 전용 고정값. */
  private static final UUID COMMANDER_ACCOUNT_ID =
      UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeee0001");

  private PublishRequestCollector eventCollector;
  private SearchAreaStateTransitionService service;

  @BeforeEach
  void setUp() {
    eventCollector = new PublishRequestCollector();
    service = new SearchAreaStateTransitionService(eventCollector);
  }

  /**
   * TC-1: ACTIVE → COMPLETED 상태 전이 시 search_area_history에 STATUS_CHANGED row가 기록된다.
   *
   * <p>S2.json entity: change_type=STATUS_CHANGED, previous_status=ACTIVE, next_status=COMPLETED,
   * changed_by_account_id=accountId, op_id=opId.
   */
  @Test
  void 상태_전이_ACTIVE_에서_COMPLETED_시_history_row가_생성된다() {
    // given
    StateTransitionCommand command =
        new StateTransitionCommand(
            BoundaryAreaFixtures.AREA_ID,
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            COMMANDER_ACCOUNT_ID,
            /* currentStatus= */ "ACTIVE",
            /* nextStatus= */ "COMPLETED",
            /* memo= */ null,
            /* clientTs= */ Instant.now(),
            /* currentVersion= */ 2L,
            GeometryFixtures.validSearchAreaPolygon());

    // when
    StateTransitionResult result = service.transition(command);

    // then
    SearchAreaHistory history = result.history();
    assertThat(history.changeType()).isEqualTo("STATUS_CHANGED");
    assertThat(history.previousStatus()).isEqualTo("ACTIVE");
    assertThat(history.nextStatus()).isEqualTo("COMPLETED");
    assertThat(history.changedByAccountId()).isEqualTo(COMMANDER_ACCOUNT_ID);
    assertThat(history.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
    assertThat(history.searchAreaId()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
  }

  /**
   * TC-2: COMPLETED → ACTIVE 상태 전이 시 search_area_history에 STATUS_CHANGED row가 기록된다.
   *
   * <p>S2.json state_machines: COMPLETED → ACTIVE 허용 (manual decision only).
   */
  @Test
  void 상태_전이_COMPLETED_에서_ACTIVE_시_history_row가_생성된다() {
    // given
    StateTransitionCommand command =
        new StateTransitionCommand(
            BoundaryAreaFixtures.AREA_ID,
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            COMMANDER_ACCOUNT_ID,
            /* currentStatus= */ "COMPLETED",
            /* nextStatus= */ "ACTIVE",
            /* memo= */ "현장 지휘관 재개 결정",
            /* clientTs= */ Instant.now(),
            /* currentVersion= */ 2L,
            GeometryFixtures.validSearchAreaPolygon());

    // when
    StateTransitionResult result = service.transition(command);

    // then
    SearchAreaHistory history = result.history();
    assertThat(history.changeType()).isEqualTo("STATUS_CHANGED");
    assertThat(history.previousStatus()).isEqualTo("COMPLETED");
    assertThat(history.nextStatus()).isEqualTo("ACTIVE");
    assertThat(history.changeMemo()).isEqualTo("현장 지휘관 재개 결정");
  }

  /**
   * TC-3: 상태 전이 후 SEARCH_AREA_CHANGED 이벤트가 발행된다.
   *
   * <p>S2.json events_published[2]: id, incidentId, opId, status, version, previousState,
   * nextState 필드 필수.
   */
  @Test
  void 상태_전이_ACTIVE_에서_COMPLETED_시_SEARCH_AREA_CHANGED_이벤트가_발행된다() {
    // given
    StateTransitionCommand command =
        new StateTransitionCommand(
            BoundaryAreaFixtures.AREA_ID,
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            COMMANDER_ACCOUNT_ID,
            /* currentStatus= */ "ACTIVE",
            /* nextStatus= */ "COMPLETED",
            /* memo= */ null,
            /* clientTs= */ Instant.now(),
            /* currentVersion= */ 2L,
            GeometryFixtures.validSearchAreaPolygon());

    // when
    service.transition(command);

    // then
    assertThat(eventCollector.collected()).hasSize(1);
    var published = eventCollector.collected().get(0);
    assertThat(published.type()).isEqualTo("SEARCH_AREA_CHANGED");
    assertThat(published.id()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
    assertThat(published.incidentId()).isEqualTo(BoundaryAreaFixtures.INCIDENT_ID);
    assertThat(published.opId()).isEqualTo(BoundaryAreaFixtures.OP1_ID);
    assertThat(published.status()).isEqualTo("COMPLETED");
    assertThat(published.previousState()).isEqualTo("ACTIVE");
    assertThat(published.nextState()).isEqualTo("COMPLETED");
    assertThat(published.version()).isGreaterThan(2L);
  }

  /**
   * TC-4: 상태 전이 후 응답의 historyCount가 증가한다.
   *
   * <p>S2.json PATCH /search-areas/{searchAreaId} response: historyCount는 search_area_history
   * 기반 파생 값이다.
   */
  @Test
  void 상태_전이_후_historyCount_가_증가한다() {
    // given
    long initialHistoryCount = 1L; // 생성 시 CREATED row 1개 기존 존재
    StateTransitionCommand command =
        new StateTransitionCommand(
            BoundaryAreaFixtures.AREA_ID,
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            COMMANDER_ACCOUNT_ID,
            /* currentStatus= */ "ACTIVE",
            /* nextStatus= */ "COMPLETED",
            /* memo= */ null,
            /* clientTs= */ Instant.now(),
            /* currentVersion= */ 2L,
            GeometryFixtures.validSearchAreaPolygon());

    // when
    StateTransitionResult result = service.transition(command);

    // then — history row가 추가됐으므로 historyCount는 initialHistoryCount + 1
    assertThat(result.historyCount()).isEqualTo(initialHistoryCount + 1);
  }

  /**
   * TC-5: 유효하지 않은 상태 전이(CANCELLED → COMPLETED)는 area_state_conflict 오류로 실패한다.
   *
   * <p>S2.json state_machines: CANCELLED 상태에서의 전이는 정의되지 않음. 거부해야 한다.
   */
  @Test
  void 유효하지_않은_상태_전이_시_area_state_conflict_로_실패한다() {
    // given
    StateTransitionCommand command =
        new StateTransitionCommand(
            BoundaryAreaFixtures.AREA_ID,
            BoundaryAreaFixtures.INCIDENT_ID,
            BoundaryAreaFixtures.OP1_ID,
            COMMANDER_ACCOUNT_ID,
            /* currentStatus= */ "CANCELLED",
            /* nextStatus= */ "COMPLETED",
            /* memo= */ null,
            /* clientTs= */ Instant.now(),
            /* currentVersion= */ 2L,
            GeometryFixtures.validSearchAreaPolygon());

    // when / then
    assertThatThrownBy(() -> service.transition(command))
        .isInstanceOf(com.surimap.maparea.statemachine.AreaStateConflictException.class);
  }
}
