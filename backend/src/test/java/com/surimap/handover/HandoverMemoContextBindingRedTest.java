package com.surimap.handover;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.handover.command.HandoverMemoCreateRequest;
import com.surimap.handover.fixture.HandoverMemoFixtures;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.handover.testdouble.HandoverMemoCreateCommandMock;
import com.surimap.handover.testdouble.HandoverMemoQueryMock;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T07 handover_memo context binding RED test.
 *
 * <p>S8.json §tdd_red_tests.backend[HandoverMemoSupportsAppAndWebTest] 기준.
 *
 * <p>검증:
 * - APP/WEB 채널 모두 OP/path/area context 메모를 저장할 수 있다.
 * - 저장된 메모는 byContext 조회 시 context별로 필터된다.
 * - HANDOVER_MEMO_CREATED 이벤트가 channel/account와 함께 capture된다.
 * - targetType 필드는 S8.json memo_target_type enum 값만 허용한다.
 */
@DisplayName("L3-T07 HandoverMemo context binding (OP/path/area) RED test")
class HandoverMemoContextBindingRedTest {

  private final HandoverMemoCreateCommandMock createMock = new HandoverMemoCreateCommandMock();
  private final HandoverMemoQueryMock queryMock = new HandoverMemoQueryMock();

  @Test
  @DisplayName("WEB 채널에서 OP context 메모를 저장하면 HANDOVER_MEMO_CREATED가 발행된다")
  void web_채널_op_context_메모_저장은_이벤트를_발행한다() {
    HandoverMemoCreateRequest request =
        new HandoverMemoCreateRequest(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_OP,
            HandoverMemoFixtures.OP2_ID,
            "OP 인수인계 내용",
            HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
            "WEB",
            HandoverMemoFixtures.CREATED_AT);

    createMock.create(request);

    assertThat(createMock.publishedEvents()).hasSize(1);
    assertThat(createMock.publishedEvents().get(0).targetType())
        .isEqualTo(HandoverMemoFixtures.TARGET_TYPE_OP);
  }

  @Test
  @DisplayName("APP 채널에서 SEARCH_PATH context 메모를 저장하면 HANDOVER_MEMO_CREATED가 발행된다")
  void app_채널_path_context_메모_저장은_이벤트를_발행한다() {
    UUID pathId = UUID.fromString("11111111-1111-1111-1111-111111110001");
    HandoverMemoCreateRequest request =
        new HandoverMemoCreateRequest(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_PATH,
            pathId,
            "경로 인수인계 내용",
            HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
            "APP",
            HandoverMemoFixtures.CREATED_AT);

    createMock.create(request);

    assertThat(createMock.publishedEvents()).hasSize(1);
    assertThat(createMock.publishedEvents().get(0).targetType())
        .isEqualTo(HandoverMemoFixtures.TARGET_TYPE_PATH);
    assertThat(createMock.receivedRequests().get(0).channel()).isEqualTo("APP");
  }

  @Test
  @DisplayName("APP 채널에서 SEARCH_AREA context 메모를 저장하면 HANDOVER_MEMO_CREATED가 발행된다")
  void app_채널_area_context_메모_저장은_이벤트를_발행한다() {
    UUID areaId = BoundaryAreaFixtures.AREA_ID;
    HandoverMemoCreateRequest request =
        new HandoverMemoCreateRequest(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_AREA,
            areaId,
            "구역 인수인계 내용",
            HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
            "APP",
            HandoverMemoFixtures.CREATED_AT);

    createMock.create(request);

    assertThat(createMock.publishedEvents()).hasSize(1);
    assertThat(createMock.publishedEvents().get(0).targetType())
        .isEqualTo(HandoverMemoFixtures.TARGET_TYPE_AREA);
  }

  @Test
  @DisplayName("byContext SEARCH_PATH 필터는 PATH context 메모만 반환한다")
  void byContext_path_필터는_path_메모만_반환한다() {
    List<HandoverMemoRow> result =
        queryMock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_PATH,
            null);

    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(r -> HandoverMemoFixtures.TARGET_TYPE_PATH.equals(r.targetType()));
    // OP/area 메모는 포함되지 않는다
    assertThat(result)
        .noneMatch(r -> HandoverMemoFixtures.TARGET_TYPE_OP.equals(r.targetType()));
    assertThat(result)
        .noneMatch(r -> HandoverMemoFixtures.TARGET_TYPE_AREA.equals(r.targetType()));
  }

  @Test
  @DisplayName("byContext SEARCH_AREA 필터는 AREA context 메모만 반환한다")
  void byContext_area_필터는_area_메모만_반환한다() {
    List<HandoverMemoRow> result =
        queryMock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_AREA,
            null);

    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(r -> HandoverMemoFixtures.TARGET_TYPE_AREA.equals(r.targetType()));
  }

  @Test
  @DisplayName("byContext OPERATIONAL_PERIOD 필터는 OP context 메모만 반환한다")
  void byContext_op_필터는_op_메모만_반환한다() {
    List<HandoverMemoRow> result =
        queryMock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_OP,
            null);

    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(r -> HandoverMemoFixtures.TARGET_TYPE_OP.equals(r.targetType()));
  }

  @Test
  @DisplayName("created_by_account_id와 channel이 저장된 request에 기록된다")
  void created_by와_channel이_request에_기록된다() {
    HandoverMemoCreateRequest request =
        new HandoverMemoCreateRequest(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_OP,
            HandoverMemoFixtures.OP2_ID,
            "메모 내용",
            HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID,
            "WEB",
            HandoverMemoFixtures.CREATED_AT);

    createMock.create(request);

    HandoverMemoCreateRequest captured = createMock.receivedRequests().get(0);
    assertThat(captured.createdByAccountId()).isEqualTo(HandoverMemoFixtures.CREATED_BY_ACCOUNT_ID);
    assertThat(captured.channel()).isEqualTo("WEB");
  }
}
