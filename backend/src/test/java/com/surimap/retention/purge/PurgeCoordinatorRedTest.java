package com.surimap.retention.purge;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.incident.event.IncidentClosedEvent;
import com.surimap.retention.purge.fixture.PurgeLifecycleFixtures;
import com.surimap.retention.purge.testdouble.MockPurgeHookRegistry;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L2-T08 S1-3 파기 오케스트레이션 RED 계약 테스트. */
@DisplayName("L2-T08 파기 오케스트레이션 계약")
class PurgeCoordinatorRedTest {

  @Test
  @DisplayName("동일한 CLOSED 사건 replay는 하나의 purge run만 생성하고 version을 중복 증가시키지 않는다")
  void closedIncidentCreatesSinglePurgeRun() {
    MockPurgeHookRegistry registry = PurgeLifecycleFixtures.defaultRegistry();
    MockEventHub eventHub = new MockEventHub();
    InMemoryIncidentDataPurgeStore store = new InMemoryIncidentDataPurgeStore();
    PurgeCoordinator coordinator = new PurgeCoordinator(store, registry.hooks(), eventHub);

    IncidentDataPurgeRun first =
        coordinator.closeIncident(
            PurgeLifecycleFixtures.INCIDENT_ID,
            PurgeLifecycleFixtures.CLOSED_AT,
            PurgeEnvironmentPolicy.DEMO_24H_SOFT_DELETE);
    IncidentDataPurgeRun replay =
        coordinator.closeIncident(
            PurgeLifecycleFixtures.INCIDENT_ID,
            PurgeLifecycleFixtures.CLOSED_AT,
            PurgeEnvironmentPolicy.DEMO_24H_SOFT_DELETE);

    assertThat(store.runsByIncident(PurgeLifecycleFixtures.INCIDENT_ID)).hasSize(1);
    assertThat(replay.purgeRunId()).isEqualTo(first.purgeRunId());
    assertThat(replay.version()).isEqualTo(first.version());
    assertThat(replay.status())
        .isIn(IncidentDataPurgeStatus.PENDING, IncidentDataPurgeStatus.RUNNING);
    assertThat(replay.purgeDeadlineTs()).isEqualTo(PurgeLifecycleFixtures.PURGE_DEADLINE_TS);
    assertThat(replay.environmentPolicy()).isEqualTo(PurgeEnvironmentPolicy.DEMO_24H_SOFT_DELETE);
  }

  @Test
  @DisplayName("INCIDENT_CLOSED handoff는 incident_data_purge run을 시작한다")
  void incidentClosedHandoffStartsPurgeRun() {
    MockPurgeHookRegistry registry = PurgeLifecycleFixtures.defaultRegistry();
    MockEventHub eventHub = new MockEventHub();
    InMemoryIncidentDataPurgeStore store = new InMemoryIncidentDataPurgeStore();
    PurgeCoordinator coordinator = new PurgeCoordinator(store, registry.hooks(), eventHub);
    IncidentClosedPurgeHandler handler = new IncidentClosedPurgeHandler(coordinator);

    IncidentDataPurgeRun run =
        handler.handle(
            new IncidentClosedEvent(
                PurgeLifecycleFixtures.INCIDENT_ID,
                "CLOSED",
                2L,
                PurgeLifecycleFixtures.CLOSED_AT,
                "incident_closed"));

    assertThat(run.incidentId()).isEqualTo(PurgeLifecycleFixtures.INCIDENT_ID);
    assertThat(run.status()).isEqualTo(IncidentDataPurgeStatus.PENDING);
    assertThat(run.purgeDeadlineTs()).isEqualTo(PurgeLifecycleFixtures.PURGE_DEADLINE_TS);
  }

  @Test
  @DisplayName("모든 훅 성공 후에만 INCIDENT_PURGED를 한 번 발행하고 purge run을 COMPLETED로 마감한다")
  void incidentPurgedPublishedOnlyAfterAllHooksSucceed() {
    MockPurgeHookRegistry registry = PurgeLifecycleFixtures.defaultRegistry();
    MockEventHub eventHub = new MockEventHub();
    InMemoryIncidentDataPurgeStore store = new InMemoryIncidentDataPurgeStore();
    PurgeCoordinator coordinator = new PurgeCoordinator(store, registry.hooks(), eventHub);
    IncidentDataPurgeRun run = openRun(coordinator);

    IncidentDataPurgeRun completed =
        coordinator.purgeIncident(PurgeLifecycleFixtures.INCIDENT_ID, run.purgeRunId());

    assertThat(completed.status()).isEqualTo(IncidentDataPurgeStatus.COMPLETED);
    assertThat(eventHub.findByType("INCIDENT_PURGED")).hasSize(1);
    assertThat(eventHub.findByType("INCIDENT_PURGED").get(0))
        .satisfies(
            event -> {
              assertThat(event.incidentId()).isEqualTo(PurgeLifecycleFixtures.INCIDENT_ID);
              assertThat(event.sourceEntityType()).isEqualTo("incident_data_purge");
              assertThat(event.sourceEntityId()).isEqualTo(run.purgeRunId());
              assertThat(event.payload())
                  .containsAllEntriesOf(
                      Map.of(
                          "id", PurgeLifecycleFixtures.INCIDENT_ID.toString(),
                          "status", "PURGED",
                          "version", completed.version(),
                          "purgeRunId", run.purgeRunId().toString()));
              assertThat(event.payload()).containsKey("purgedAt");
            });
  }

  @Test
  @DisplayName("retainedCount가 있는 WAITING_FOR_SYNC 훅은 INCIDENT_PURGED 발행을 막는다")
  void retainedWaitingHookBlocksPublish() {
    MockPurgeHookRegistry registry =
        PurgeLifecycleFixtures.defaultRegistry()
            .withResult(PurgeHookName.LOCAL_SYNC, PurgeLifecycleFixtures.waitingForSyncResult());
    MockEventHub eventHub = new MockEventHub();
    InMemoryIncidentDataPurgeStore store = new InMemoryIncidentDataPurgeStore();
    PurgeCoordinator coordinator = new PurgeCoordinator(store, registry.hooks(), eventHub);
    IncidentDataPurgeRun run = openRun(coordinator);

    IncidentDataPurgeRun waiting =
        coordinator.purgeIncident(PurgeLifecycleFixtures.INCIDENT_ID, run.purgeRunId());

    assertThat(waiting.status()).isEqualTo(IncidentDataPurgeStatus.WAITING_FOR_SYNC);
    assertThat(waiting.lastErrorCode())
        .isEqualTo(PurgeLifecycleFixtures.WAITING_FOR_SYNC_ERROR_CODE);
    assertThat(eventHub.findByType("INCIDENT_PURGED")).isEmpty();
  }

  @Test
  @DisplayName("완료된 purge run의 scheduler retry와 CLOSED replay는 INCIDENT_PURGED를 중복 발행하지 않는다")
  void duplicateRetryDoesNotDuplicatePublish() {
    MockPurgeHookRegistry registry = PurgeLifecycleFixtures.defaultRegistry();
    MockEventHub eventHub = new MockEventHub();
    InMemoryIncidentDataPurgeStore store = new InMemoryIncidentDataPurgeStore();
    PurgeCoordinator coordinator = new PurgeCoordinator(store, registry.hooks(), eventHub);
    IncidentDataPurgeRun run = openRun(coordinator);

    IncidentDataPurgeRun first =
        coordinator.purgeIncident(PurgeLifecycleFixtures.INCIDENT_ID, run.purgeRunId());
    IncidentDataPurgeRun retry =
        coordinator.purgeIncident(PurgeLifecycleFixtures.INCIDENT_ID, run.purgeRunId());
    IncidentDataPurgeRun closeReplay =
        coordinator.closeIncident(
            PurgeLifecycleFixtures.INCIDENT_ID,
            PurgeLifecycleFixtures.CLOSED_AT,
            PurgeEnvironmentPolicy.DEMO_24H_SOFT_DELETE);

    assertThat(retry.status()).isEqualTo(IncidentDataPurgeStatus.COMPLETED);
    assertThat(closeReplay.purgeRunId()).isEqualTo(first.purgeRunId());
    assertThat(closeReplay.version()).isEqualTo(first.version());
    assertThat(eventHub.findByType("INCIDENT_PURGED")).hasSize(1);
  }

  private static IncidentDataPurgeRun openRun(PurgeCoordinator coordinator) {
    return coordinator.closeIncident(
        PurgeLifecycleFixtures.INCIDENT_ID,
        PurgeLifecycleFixtures.CLOSED_AT,
        PurgeEnvironmentPolicy.DEMO_24H_SOFT_DELETE);
  }
}
