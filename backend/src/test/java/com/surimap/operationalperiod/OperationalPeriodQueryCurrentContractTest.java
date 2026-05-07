package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.fixture.OperationalPeriodQueryFixtures;
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.operationalperiod.testdouble.OperationalPeriodQueryMock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T05A OperationalPeriodQuery.current/list mock contract 테스트.
 *
 * <p>real DB query 없이 S1-1/S3-1/S5/S7 소비 Lane이 current OP id/status/version을 검증할 수 있다.
 */
@DisplayName("L3-T05A OperationalPeriodQuery current/list mock contract")
class OperationalPeriodQueryCurrentContractTest {

  private final OperationalPeriodQueryMock mock = new OperationalPeriodQueryMock();

  @Test
  @DisplayName("알려진 incidentId는 OP1 ACTIVE current result를 반환한다")
  void known_incidentId는_op1_active_current_result를_반환한다() {
    Optional<CurrentOpResult> result = mock.current(OperationalPeriodFixtures.INCIDENT_ID);

    assertThat(result).isPresent();
    CurrentOpResult row = result.get();
    assertThat(row.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(row.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(row.status()).isEqualTo("ACTIVE");
    assertThat(row.reason()).isEqualTo("INITIAL");
    assertThat(row.sequenceNo()).isEqualTo(1);
    assertThat(row.version()).isEqualTo(1L);
  }

  @Test
  @DisplayName("알 수 없는 incidentId는 current empty를 반환한다")
  void 알_수_없는_incidentId는_current_empty를_반환한다() {
    Optional<CurrentOpResult> result = mock.current(UUID.randomUUID());

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("list는 sequenceNo ASC 정렬과 current OP 공유 fixture를 제공한다")
  void list는_sequenceNo_ASC_정렬과_current_op_공유_fixture를_제공한다() {
    List<OperationalPeriodRow> list = mock.list(OperationalPeriodFixtures.INCIDENT_ID);
    CurrentOpResult current = mock.current(OperationalPeriodFixtures.INCIDENT_ID).orElseThrow();

    assertThat(list).containsExactly(OperationalPeriodQueryFixtures.currentOp1());
    assertThat(list.get(0).opId()).isEqualTo(current.opId());
    assertThat(list.get(0).version()).isEqualTo(current.version());
  }

  @Test
  @DisplayName("S7/S3-1/S5 소비자는 같은 current OP id/status/version을 비교한다")
  void 소비자는_같은_current_op_fixture를_참조한다() {
    CurrentOpResult row = mock.current(OperationalPeriodFixtures.INCIDENT_ID).orElseThrow();
    OperationalPeriodRow fixture = OperationalPeriodQueryFixtures.currentOp1();

    assertThat(row.opId()).isEqualTo(fixture.opId());
    assertThat(row.status()).isEqualTo(fixture.status());
    assertThat(row.version()).isEqualTo(fixture.version());
    assertThat(row.sequenceNo()).isEqualTo(fixture.sequenceNo());
  }
}
