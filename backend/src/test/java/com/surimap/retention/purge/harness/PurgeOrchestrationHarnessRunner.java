package com.surimap.retention.purge.harness;

/**
 * L2-T09C 파기 오케스트레이션 하네스 러너.
 *
 * <p>다른 Lane 테스트에서 파기 오케스트레이션 계약을 실행하고 증거를 얻기 위해 사용한다.
 * 생성 시 eager하게 closeAndPurge를 호출한다.
 */
public final class PurgeOrchestrationHarnessRunner {

  private final PurgeOrchestrationHarnessFixtures.PurgeEvidence purgeEvidence;

  private PurgeOrchestrationHarnessRunner(
      PurgeOrchestrationContract contract,
      PurgeOrchestrationHarnessFixtures.HarnessFixture fixture) {
    this.purgeEvidence = contract.closeAndPurge(fixture);
  }

  /**
   * mock 계약으로 러너를 생성한다.
   *
   * @param fixture 하네스 픽스처
   * @return mock 계약을 사용하는 하네스 러너
   */
  public static PurgeOrchestrationHarnessRunner mock(
      PurgeOrchestrationHarnessFixtures.HarnessFixture fixture) {
    return run(new MockPurgeOrchestrationContract(), fixture);
  }

  /**
   * 실제 S1-3 오케스트레이션 계약으로 러너를 생성한다.
   *
   * @param fixture 하네스 픽스처
   * @return InMemoryS1_3 계약을 사용하는 하네스 러너
   */
  public static PurgeOrchestrationHarnessRunner inMemoryS1_3(
      PurgeOrchestrationHarnessFixtures.HarnessFixture fixture) {
    return run(new InMemoryS1_3PurgeOrchestrationContract(), fixture);
  }

  /**
   * 주어진 계약으로 러너를 생성한다.
   *
   * @param contract 파기 오케스트레이션 계약
   * @param fixture  하네스 픽스처
   * @return 주어진 계약을 사용하는 하네스 러너
   */
  public static PurgeOrchestrationHarnessRunner run(
      PurgeOrchestrationContract contract,
      PurgeOrchestrationHarnessFixtures.HarnessFixture fixture) {
    return new PurgeOrchestrationHarnessRunner(contract, fixture);
  }

  /** 파기 실행 결과 증거를 반환한다. */
  public PurgeOrchestrationHarnessFixtures.PurgeEvidence purgeEvidence() {
    return purgeEvidence;
  }
}
