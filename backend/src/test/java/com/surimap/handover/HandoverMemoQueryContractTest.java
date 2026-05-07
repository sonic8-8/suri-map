package com.surimap.handover;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.handover.fixture.HandoverMemoFixtures;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.handover.testdouble.HandoverMemoQueryMock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T07 HandoverMemoQuery.byContext mock contract 테스트.
 *
 * <p>S8.json §service_contracts HandoverMemoQuery.byContext 기준. real DB 없이 소비 Lane(S3-2)이
 * context별 memoId/incidentId/opId/targetType/status/version 계약을 검증한다.
 *
 * <p>harness fixture: sc11_handover_ai_convergence.expectedQueries.HandoverMemoQuery.byContext
 */
@DisplayName("L3-T07 HandoverMemoQuery.byContext mock contract")
class HandoverMemoQueryContractTest {

  private final HandoverMemoQueryMock mock = new HandoverMemoQueryMock();

  @Test
  @DisplayName("알려진 incidentId+opId는 메모 컬렉션을 반환한다")
  void known_incidentId_opId는_메모_컬렉션을_반환한다() {
    List<HandoverMemoRow> result =
        mock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            null,
            null);

    assertThat(result).isNotEmpty();
  }

  @Test
  @DisplayName("알 수 없는 incidentId는 빈 컬렉션을 반환한다")
  void 알_수_없는_incidentId는_빈_컬렉션을_반환한다() {
    List<HandoverMemoRow> result =
        mock.byContext(UUID.randomUUID(), null, null, null);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("OP context 메모 row는 sc11 harness fixture의 memoId/opId/version을 포함한다")
  void op_context_메모_row는_harness_fixture_값을_포함한다() {
    List<HandoverMemoRow> result =
        mock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_OP,
            null);

    assertThat(result).hasSize(1);
    HandoverMemoRow row = result.get(0);

    // S8.json harness_fixtures.sc11_handover_ai_convergence.expectedQueries.HandoverMemoQuery.byContext
    assertThat(row.memoId()).isEqualTo(HandoverMemoFixtures.MEMO_ID);
    assertThat(row.opId()).isEqualTo(HandoverMemoFixtures.OP2_ID);
    assertThat(row.incidentId()).isEqualTo(HandoverMemoFixtures.INCIDENT_ID);
    assertThat(row.version()).isEqualTo(HandoverMemoFixtures.MEMO_VERSION);
  }

  @Test
  @DisplayName("targetType=SEARCH_PATH 필터는 path context 메모만 반환한다")
  void targetType_SEARCH_PATH_필터는_path_context만_반환한다() {
    List<HandoverMemoRow> result =
        mock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_PATH,
            null);

    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(r -> HandoverMemoFixtures.TARGET_TYPE_PATH.equals(r.targetType()));
  }

  @Test
  @DisplayName("targetType=SEARCH_AREA 필터는 area context 메모만 반환한다")
  void targetType_SEARCH_AREA_필터는_area_context만_반환한다() {
    List<HandoverMemoRow> result =
        mock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_AREA,
            null);

    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(r -> HandoverMemoFixtures.TARGET_TYPE_AREA.equals(r.targetType()));
  }

  @Test
  @DisplayName("targetType=OPERATIONAL_PERIOD 필터는 OP context 메모만 반환한다")
  void targetType_OP_필터는_op_context만_반환한다() {
    List<HandoverMemoRow> result =
        mock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            HandoverMemoFixtures.OP2_ID,
            HandoverMemoFixtures.TARGET_TYPE_OP,
            null);

    assertThat(result).isNotEmpty();
    assertThat(result)
        .allMatch(r -> HandoverMemoFixtures.TARGET_TYPE_OP.equals(r.targetType()));
  }

  @Test
  @DisplayName("알 수 없는 opId는 빈 컬렉션을 반환한다")
  void 알_수_없는_opId는_빈_컬렉션을_반환한다() {
    List<HandoverMemoRow> result =
        mock.byContext(
            HandoverMemoFixtures.INCIDENT_ID,
            UUID.randomUUID(),
            null,
            null);

    assertThat(result).isEmpty();
  }
}
