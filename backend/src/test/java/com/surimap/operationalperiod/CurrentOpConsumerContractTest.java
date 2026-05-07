package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.operationalperiod.fixture.CurrentOpConsumerFixtures;
import com.surimap.operationalperiod.fixture.OpBootstrapFixtures;
import com.surimap.operationalperiod.testdouble.Op1BootstrapMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L3-T05A: S7/S3-1/S5 current OP 소비자 mock contract 테스트.
 *
 * <p>S8은 current OP만 제공하고 다른 Lane owner domain row를 만들지 않는다.
 */
@DisplayName("L3-T05A current OP consumer contract")
class CurrentOpConsumerContractTest {

  @Test
  @DisplayName("S7/S3-1/S5 소비자는 같은 OP1 opId/sequenceNo/version fixture를 사용한다")
  void current_op_소비자는_같은_op1_fixture를_사용한다() {
    var probes = CurrentOpConsumerFixtures.currentOpConsumerProbes();
    var completion = new Op1BootstrapMock().handle(OpBootstrapFixtures.incidentCreatedEvent());

    assertThat(probes).hasSize(3);
    assertThat(probes)
        .extracting(CurrentOpConsumerFixtures.CurrentOpConsumerProbe::consumer)
        .containsExactlyElementsOf(CurrentOpConsumerFixtures.CURRENT_OP_CONSUMERS);
    assertThat(probes)
        .allSatisfy(
            probe -> {
              assertThat(probe.opId()).isEqualTo(completion.opId());
              assertThat(probe.sequenceNo()).isEqualTo(completion.sequenceNo());
              assertThat(probe.version()).isEqualTo(completion.version());
            });
  }

  @Test
  @DisplayName("S8 current OP mock은 S7/S3-1/S5 owner domain write를 만들지 않는다")
  void current_op_mock은_다른_lane_owner_write를_만들지_않는다() {
    assertThat(CurrentOpConsumerFixtures.FORBIDDEN_S8_OWNER_WRITES)
        .containsExactly(
            "offline_package_manifest",
            "offline_package_status",
            "search_session",
            "search_path",
            "path_segment",
            "marker",
            "photo",
            "notification_delivery");
  }
}
