package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.fixture.EventFixtures;
import com.surimap.eventhub.harness.EventHubContract;
import com.surimap.eventhub.harness.EventHubHarnessFixtures;
import com.surimap.eventhub.harness.EventHubHarnessFixtures.HarnessRequest;
import com.surimap.eventhub.harness.EventHubHarnessFixtures.PublishEvidence;
import com.surimap.eventhub.harness.EventHubHarnessFixtures.SseReplayEvidence;
import com.surimap.eventhub.harness.EventHubHarnessRunner;
import com.surimap.eventhub.harness.InMemoryS4EventHubContract;
import com.surimap.eventhub.harness.MockEventHubContract;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T09B event hub harness runner RED")
class EventHubHarnessRunnerRedTest {

  @Test
  @DisplayName("mock runner exposes publish evidence with sc08 fixture ids")
  void mock_runner_exposes_publish_evidence_with_sc08_fixture_ids() {
    EventHubHarnessRunner runner =
        EventHubHarnessRunner.mock(EventHubHarnessFixtures.sc08SupportRequest());

    assertThat(runner.publishEvidence().eventId()).isEqualTo(EventFixtures.SC08_EVENT_ID);
    assertThat(runner.publishEvidence().incidentId()).isEqualTo(EventFixtures.INCIDENT_ID_01);
    assertThat(runner.publishEvidence().type()).isEqualTo("SUPPORT_REQUEST_CREATED");
    assertThat(runner.publishEvidence().payload())
        .containsEntry("id", "50000000-0000-4000-8000-000000000801");
    assertThat(runner.publishEvidence().payload()).containsEntry("status", "REQUESTED");
    assertThat(runner.publishEvidence().payload()).containsEntry("version", 1);
  }

  @Test
  @DisplayName("mock runner exposes publish evidence with sc09 fixture ids")
  void mock_runner_exposes_publish_evidence_with_sc09_fixture_ids() {
    EventHubHarnessRunner runner =
        EventHubHarnessRunner.mock(EventHubHarnessFixtures.sc09PathAppended());

    assertThat(runner.publishEvidence().eventId()).isEqualTo(EventFixtures.SC09_EVENT_ID);
    assertThat(runner.publishEvidence().type()).isEqualTo("PATH_APPENDED");
    assertThat(runner.publishEvidence().payload()).containsEntry("status", "RECORDING");
    assertThat(runner.publishEvidence().payload()).containsEntry("version", 7);
  }

  @Test
  @DisplayName("mock and inMemoryS4 contracts produce same publish evidence")
  void mock_and_inMemoryS4_contracts_produce_same_publish_evidence() {
    HarnessRequest fixture = EventHubHarnessFixtures.sc08SupportRequest();
    EventHubContract mock = new MockEventHubContract();
    EventHubContract inMemory = new InMemoryS4EventHubContract();

    PublishEvidence mockEvidence = mock.publish(fixture);
    mock.reset();
    inMemory.reset();
    PublishEvidence inMemoryEvidence = inMemory.publish(fixture);

    assertThat(mockEvidence.eventId()).isEqualTo(inMemoryEvidence.eventId());
    assertThat(mockEvidence.incidentId()).isEqualTo(inMemoryEvidence.incidentId());
    assertThat(mockEvidence.type()).isEqualTo(inMemoryEvidence.type());
    assertThat(mockEvidence.payload()).isEqualTo(inMemoryEvidence.payload());
  }

  @Test
  @DisplayName("inMemoryS4 provides sse replay evidence with positive monotonic sequence")
  void inMemoryS4_provides_sse_replay_evidence_with_positive_monotonic_sequence() {
    EventHubContract contract = new InMemoryS4EventHubContract();
    contract.publish(EventHubHarnessFixtures.sc08SupportRequest());
    contract.publish(EventHubHarnessFixtures.sc09PathAppended());

    Optional<SseReplayEvidence> sc08Replay = contract.replayEvidence(EventFixtures.SC08_EVENT_ID);
    Optional<SseReplayEvidence> sc09Replay = contract.replayEvidence(EventFixtures.SC09_EVENT_ID);

    assertThat(sc08Replay).isPresent();
    assertThat(sc09Replay).isPresent();
    assertThat(sc08Replay.get().replaySequence()).isPositive();
    assertThat(sc09Replay.get().replaySequence())
        .isGreaterThan(sc08Replay.get().replaySequence());
    assertThat(sc08Replay.get().eventId()).isEqualTo(EventFixtures.SC08_EVENT_ID);
  }

  @Test
  @DisplayName("duplicate publish to inMemoryS4 does not duplicate sse replay")
  void duplicate_publish_to_inMemoryS4_does_not_duplicate_sse_replay() {
    EventHubContract contract = new InMemoryS4EventHubContract();
    contract.publish(EventHubHarnessFixtures.dedupeEventFirst());
    contract.publish(EventHubHarnessFixtures.dedupeEventFirst());

    Optional<SseReplayEvidence> replay1 = contract.replayEvidence(EventFixtures.DEDUPE_EVENT_ID);
    assertThat(contract.publishCount()).isEqualTo(2);
    assertThat(replay1).isPresent();
    // SSE replay store는 동일 eventId를 한 번만 기록 (InMemorySseReplayEventStore 특성)
    // SSE replay store에는 동일 eventId가 한 번만 기록된다 (sseEventLogRows: 1)
    assertThat(contract.sseReplayCountForIncident(EventFixtures.INCIDENT_ID_01)).isEqualTo(1);
  }
}
