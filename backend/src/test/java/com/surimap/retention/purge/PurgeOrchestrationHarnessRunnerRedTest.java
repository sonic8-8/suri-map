package com.surimap.retention.purge;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.retention.purge.fixture.PurgeLifecycleFixtures;
import com.surimap.retention.purge.harness.InMemoryS1_3PurgeOrchestrationContract;
import com.surimap.retention.purge.harness.MockPurgeOrchestrationContract;
import com.surimap.retention.purge.harness.PurgeOrchestrationContract;
import com.surimap.retention.purge.harness.PurgeOrchestrationHarnessFixtures;
import com.surimap.retention.purge.harness.PurgeOrchestrationHarnessFixtures.HarnessFixture;
import com.surimap.retention.purge.harness.PurgeOrchestrationHarnessFixtures.PurgeEvidence;
import com.surimap.retention.purge.harness.PurgeOrchestrationHarnessRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L2-T09C 파기 오케스트레이션 mock contract 하네스 RED 테스트. */
@DisplayName("L2-T09C 파기 오케스트레이션 하네스 계약")
class PurgeOrchestrationHarnessRunnerRedTest {

  @Test
  @DisplayName("mock runner는 SC-12 픽스처에 대한 파기 증거를 노출한다")
  void mock_runner_exposes_purge_evidence_for_sc12_fixture() {
    HarnessFixture sc12 = PurgeOrchestrationHarnessFixtures.sc12AllHooksSucceed();

    PurgeOrchestrationHarnessRunner runner = PurgeOrchestrationHarnessRunner.mock(sc12);
    PurgeEvidence evidence = runner.purgeEvidence();

    assertThat(evidence.finalStatus()).isEqualTo(IncidentDataPurgeStatus.COMPLETED);
    assertThat(evidence.incidentPurgedEventCount()).isEqualTo(1);
    assertThat(evidence.incidentId()).isEqualTo(PurgeLifecycleFixtures.INCIDENT_ID);
    assertThat(evidence.purgeRunId()).isNotNull();
  }

  @Test
  @DisplayName("mock과 inMemoryS1_3 계약은 동일한 파기 증거를 생성한다")
  void mock_and_inMemoryS1_3_contracts_produce_same_purge_evidence() {
    HarnessFixture sc12 = PurgeOrchestrationHarnessFixtures.sc12AllHooksSucceed();

    PurgeOrchestrationHarnessRunner mockRunner = PurgeOrchestrationHarnessRunner.mock(sc12);
    PurgeOrchestrationHarnessRunner realRunner = PurgeOrchestrationHarnessRunner.inMemoryS1_3(sc12);

    PurgeEvidence mockEvidence = mockRunner.purgeEvidence();
    PurgeEvidence realEvidence = realRunner.purgeEvidence();

    assertThat(mockEvidence.incidentId()).isEqualTo(realEvidence.incidentId());
    assertThat(mockEvidence.purgeRunId()).isEqualTo(realEvidence.purgeRunId());
    assertThat(mockEvidence.finalStatus()).isEqualTo(realEvidence.finalStatus());
    assertThat(mockEvidence.incidentPurgedEventCount())
        .isEqualTo(realEvidence.incidentPurgedEventCount());
  }

  @Test
  @DisplayName("inMemoryS1_3는 모든 훅 성공 후 INCIDENT_PURGED를 한 번 발행하고 COMPLETED 상태를 반환한다")
  void inMemoryS1_3_publishes_INCIDENT_PURGED_once_after_all_hooks_succeed() {
    HarnessFixture sc12 = PurgeOrchestrationHarnessFixtures.sc12AllHooksSucceed();

    PurgeOrchestrationHarnessRunner runner = PurgeOrchestrationHarnessRunner.inMemoryS1_3(sc12);
    PurgeEvidence evidence = runner.purgeEvidence();

    assertThat(evidence.incidentPurgedEventCount()).isEqualTo(1);
    assertThat(evidence.finalStatus()).isEqualTo(IncidentDataPurgeStatus.COMPLETED);
  }

  @Test
  @DisplayName("inMemoryS1_3에 closeAndPurge를 중복 호출해도 INCIDENT_PURGED는 한 번만 발행된다")
  void duplicate_closeAndPurge_to_inMemoryS1_3_does_not_duplicate_INCIDENT_PURGED() {
    HarnessFixture sc12 = PurgeOrchestrationHarnessFixtures.sc12AllHooksSucceed();
    InMemoryS1_3PurgeOrchestrationContract contract = new InMemoryS1_3PurgeOrchestrationContract();

    PurgeEvidence firstEvidence = contract.closeAndPurge(sc12);
    PurgeEvidence secondEvidence = contract.closeAndPurge(sc12);

    assertThat(firstEvidence.incidentPurgedEventCount()).isEqualTo(1);
    assertThat(secondEvidence.incidentPurgedEventCount()).isEqualTo(1);
    assertThat(secondEvidence.finalStatus()).isEqualTo(IncidentDataPurgeStatus.COMPLETED);
  }
}
