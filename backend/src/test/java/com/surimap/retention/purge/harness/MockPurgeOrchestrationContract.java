package com.surimap.retention.purge.harness;

import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.retention.purge.IncidentDataPurgeStatus;
import com.surimap.retention.purge.IncidentPurgedPublishRequest;
import com.surimap.retention.purge.PurgeHook;
import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.testdouble.MockPurgeHookRegistry;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 순수 in-memory mock 파기 오케스트레이션 계약.
 *
 * <p>PurgeCoordinator를 사용하지 않는 stub 구현. 픽스처 ID 안정성 검증을 위해 사용된다.
 * MockPurgeHookRegistry와 MockEventHub를 사용하며 결정론적 purgeRunId를 생성한다.
 */
public final class MockPurgeOrchestrationContract implements PurgeOrchestrationContract {

  private final MockPurgeHookRegistry registry;
  private final MockEventHub eventHub;

  public MockPurgeOrchestrationContract() {
    this.registry = new MockPurgeHookRegistry();
    this.eventHub = new MockEventHub();
  }

  @Override
  public PurgeOrchestrationHarnessFixtures.PurgeEvidence closeAndPurge(
      PurgeOrchestrationHarnessFixtures.HarnessFixture fixture) {
    UUID incidentId = fixture.incidentId();
    UUID purgeRunId = deterministicPurgeRunId(incidentId);

    PurgeHookRequest request =
        new PurgeHookRequest(
            incidentId,
            purgeRunId,
            fixture.closedAt(),
            fixture.environmentPolicy().purgeDueAt(fixture.closedAt()));

    for (PurgeHook hook : registry.hooks()) {
      hook.purge(request);
    }

    eventHub.publish(
        new IncidentPurgedPublishRequest(incidentId, 3L, purgeRunId, fixture.closedAt())
            .toPublishRequest());

    int count = eventHub.findByType(IncidentPurgedPublishRequest.TYPE).size();

    return new PurgeOrchestrationHarnessFixtures.PurgeEvidence(
        incidentId, purgeRunId, IncidentDataPurgeStatus.COMPLETED, count);
  }

  @Override
  public void reset() {
    eventHub.reset();
    registry.resetObservations();
  }

  private static UUID deterministicPurgeRunId(UUID incidentId) {
    return UUID.nameUUIDFromBytes(
        ("incident_data_purge:" + incidentId).getBytes(StandardCharsets.UTF_8));
  }
}
