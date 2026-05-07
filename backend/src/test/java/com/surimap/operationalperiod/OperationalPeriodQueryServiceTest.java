package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQueryService;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L3-T05B OperationalPeriodQueryService 단위 테스트. */
@DisplayName("L3-T05B OperationalPeriodQueryService")
class OperationalPeriodQueryServiceTest {

  private final OperationalPeriodMapper mapper = mock(OperationalPeriodMapper.class);
  private final OperationalPeriodQueryService service = new OperationalPeriodQueryService(mapper);

  @Test
  @DisplayName("ACTIVE OP가 있으면 current()는 CurrentOpResult를 반환한다")
  void active_op가_있으면_current_op를_반환한다() {
    OperationalPeriod op =
        new OperationalPeriod(
            OperationalPeriodFixtures.CURRENT_OP_ID,
            OperationalPeriodFixtures.INCIDENT_ID,
            OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO,
            OperationalPeriodFixtures.CURRENT_OP_STATUS,
            OperationalPeriodFixtures.CURRENT_OP_REASON,
            null,
            null,
            null,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT,
            null,
            OperationalPeriodFixtures.CURRENT_OP_VERSION,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT);

    when(mapper.findActiveByIncident(OperationalPeriodFixtures.INCIDENT_ID))
        .thenReturn(Optional.of(op));

    Optional<CurrentOpResult> result = service.current(OperationalPeriodFixtures.INCIDENT_ID);

    assertThat(result).isPresent();
    CurrentOpResult cur = result.get();
    assertThat(cur.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(cur.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(cur.status()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STATUS);
    assertThat(cur.sequenceNo()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO);
    assertThat(cur.startedAt()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STARTED_AT);
    assertThat(cur.endedAt()).isNull();
    assertThat(cur.reason()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_REASON);
    assertThat(cur.version()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_VERSION);
  }

  @Test
  @DisplayName("ACTIVE OP가 없으면 current()는 empty를 반환한다")
  void active_op가_없으면_empty를_반환한다() {
    when(mapper.findActiveByIncident(OperationalPeriodFixtures.INCIDENT_ID))
        .thenReturn(Optional.empty());

    Optional<CurrentOpResult> result = service.current(OperationalPeriodFixtures.INCIDENT_ID);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("list()는 sequenceNumber 오름차순 OperationalPeriodRow 목록을 반환한다")
  void list_는_op_목록을_반환한다() {
    OperationalPeriod op =
        new OperationalPeriod(
            OperationalPeriodFixtures.CURRENT_OP_ID,
            OperationalPeriodFixtures.INCIDENT_ID,
            OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO,
            OperationalPeriodFixtures.CURRENT_OP_STATUS,
            OperationalPeriodFixtures.CURRENT_OP_REASON,
            null,
            null,
            null,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT,
            null,
            OperationalPeriodFixtures.CURRENT_OP_VERSION,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT);

    when(mapper.findAllByIncidentOrderBySequence(OperationalPeriodFixtures.INCIDENT_ID))
        .thenReturn(List.of(op));

    List<OperationalPeriodRow> rows = service.list(OperationalPeriodFixtures.INCIDENT_ID);

    assertThat(rows).hasSize(1);
    OperationalPeriodRow row = rows.get(0);
    assertThat(row.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(row.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(row.status()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_STATUS);
    assertThat(row.sequenceNo()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO);
    assertThat(row.reason()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_REASON);
    assertThat(row.version()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_VERSION);
  }
}
