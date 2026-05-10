package com.surimap.retention.purge.harness;

import com.surimap.retention.purge.IncidentDataPurgeStatus;
import com.surimap.retention.purge.PurgeEnvironmentPolicy;
import com.surimap.retention.purge.fixture.PurgeLifecycleFixtures;
import java.time.Instant;
import java.util.UUID;

/**
 * SC-12 하네스 픽스처: 모든 훅 성공 → INCIDENT_PURGED 발행.
 *
 * <p>mock contract와 real S1-3 orchestration contract가 동일한 픽스처 ID로 동작하는지 검증하기 위한
 * 안정적인 픽스처 데이터.
 */
public final class PurgeOrchestrationHarnessFixtures {

  private PurgeOrchestrationHarnessFixtures() {}

  /**
   * SC-12: 모든 파기 훅이 성공하면 INCIDENT_PURGED가 발행되어야 한다.
   *
   * <p>{@link PurgeLifecycleFixtures} 상수를 사용하고 DEMO_24H_SOFT_DELETE 정책을 적용한다.
   */
  public static HarnessFixture sc12AllHooksSucceed() {
    return new HarnessFixture(
        PurgeLifecycleFixtures.INCIDENT_ID,
        PurgeLifecycleFixtures.CLOSED_AT,
        PurgeEnvironmentPolicy.DEMO_24H_SOFT_DELETE);
  }

  /**
   * 하네스 픽스처 입력 값.
   *
   * @param incidentId  파기 대상 사건 ID
   * @param closedAt    사건 종료 시각
   * @param environmentPolicy 파기 환경 정책
   */
  public record HarnessFixture(
      UUID incidentId, Instant closedAt, PurgeEnvironmentPolicy environmentPolicy) {}

  /**
   * 파기 실행 결과 증거.
   *
   * @param incidentId             파기된 사건 ID
   * @param purgeRunId             파기 실행 ID
   * @param finalStatus            최종 파기 상태
   * @param incidentPurgedEventCount INCIDENT_PURGED 이벤트 발행 횟수
   */
  public record PurgeEvidence(
      UUID incidentId,
      UUID purgeRunId,
      IncidentDataPurgeStatus finalStatus,
      int incidentPurgedEventCount) {}
}
