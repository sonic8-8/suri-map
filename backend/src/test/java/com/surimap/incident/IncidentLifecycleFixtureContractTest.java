package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.incident.fixture.IncidentLifecycleFixtureLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L1-T02 사건 lifecycle 소비 fixture")
class IncidentLifecycleFixtureContractTest {

  @Test
  @DisplayName("SC-12 종료 후 requeue fixture는 CLOSED_NO_RETRY 상태를 고정한다")
  void sc12TerminalRequeueFixtureFreezesClosedNoRetryState() {
    var states = IncidentLifecycleFixtureLoader.loadTerminalStateRules();

    assertThat(states.closedIncidentId()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0012");
    assertThat(states.expectedPostClose()).isEqualTo("FAILED_FINAL");
    assertThat(states.expectedUserCategory()).isEqualTo("CLOSED_NO_RETRY");
    assertThat(states.closedFailureRow().operationAlias()).isEqualTo("op-fail-closed-001");
    assertThat(states.closedFailureRow().operationId())
        .isEqualTo("66666666-0000-4000-8000-000000001704");
    assertThat(states.closedFailureRow().userSafeFailureCategory()).isEqualTo("CLOSED_NO_RETRY");
    assertThat(states.closedFailureRow().retryable()).isFalse();
  }
}
