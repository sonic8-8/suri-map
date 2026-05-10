package com.surimap.retention.purge.harness;

/**
 * L2-T09C 파기 오케스트레이션 계약 경계.
 *
 * <p>mock 구현({@link MockPurgeOrchestrationContract})과 실제 S1-3 오케스트레이션 구현
 * ({@link InMemoryS1_3PurgeOrchestrationContract})을 픽스처 ID 변경 없이 교체할 수 있는 스왑 경계.
 */
public interface PurgeOrchestrationContract {

  /**
   * 사건 종료 및 파기를 수행하고 증거를 반환한다.
   *
   * @param fixture 하네스 픽스처 입력 값
   * @return 파기 실행 결과 증거
   */
  PurgeOrchestrationHarnessFixtures.PurgeEvidence closeAndPurge(
      PurgeOrchestrationHarnessFixtures.HarnessFixture fixture);

  /** 상태를 초기화한다. 다음 closeAndPurge 호출을 위해 내부 상태를 비운다. */
  void reset();
}
