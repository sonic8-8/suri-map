package com.surimap.retention.purge.harness;

import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.retention.purge.IncidentDataPurgeRun;
import com.surimap.retention.purge.IncidentPurgedPublishRequest;
import com.surimap.retention.purge.InMemoryIncidentDataPurgeStore;
import com.surimap.retention.purge.PurgeCoordinator;
import com.surimap.retention.purge.testdouble.MockPurgeHookRegistry;

/**
 * 실제 PurgeCoordinator를 사용하는 S1-3 파기 오케스트레이션 계약 구현.
 *
 * <p>MockPurgeHookRegistry, InMemoryIncidentDataPurgeStore, MockEventHub를 실제 PurgeCoordinator에
 * 주입하여 실제 오케스트레이션 흐름을 검증한다. reset() 시 store와 coordinator를 재생성한다.
 */
public final class InMemoryS1_3PurgeOrchestrationContract implements PurgeOrchestrationContract {

  private final MockPurgeHookRegistry registry;
  private MockEventHub eventHub;
  private InMemoryIncidentDataPurgeStore store;
  private PurgeCoordinator coordinator;

  public InMemoryS1_3PurgeOrchestrationContract() {
    this.registry = new MockPurgeHookRegistry();
    this.eventHub = new MockEventHub();
    this.store = new InMemoryIncidentDataPurgeStore();
    this.coordinator = new PurgeCoordinator(store, registry.hooks(), eventHub);
  }

  @Override
  public PurgeOrchestrationHarnessFixtures.PurgeEvidence closeAndPurge(
      PurgeOrchestrationHarnessFixtures.HarnessFixture fixture) {
    IncidentDataPurgeRun run =
        coordinator.closeIncident(
            fixture.incidentId(), fixture.closedAt(), fixture.environmentPolicy());

    IncidentDataPurgeRun completed =
        coordinator.purgeIncident(run.incidentId(), run.purgeRunId());

    int count = eventHub.findByType(IncidentPurgedPublishRequest.TYPE).size();

    return new PurgeOrchestrationHarnessFixtures.PurgeEvidence(
        completed.incidentId(), completed.purgeRunId(), completed.status(), count);
  }

  @Override
  public void reset() {
    eventHub.reset();
    registry.resetObservations();
    store = new InMemoryIncidentDataPurgeStore();
    coordinator = new PurgeCoordinator(store, registry.hooks(), eventHub);
  }
}
