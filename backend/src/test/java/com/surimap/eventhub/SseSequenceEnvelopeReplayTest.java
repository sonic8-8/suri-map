package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.stream.InMemorySseReplayEventStore;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseEventFrameFormatter;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T07A Last-Event-ID replay sequence")
class SseSequenceEnvelopeReplayTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

  private final InMemorySseReplayEventStore replayStore = new InMemorySseReplayEventStore();
  private final SseReplayService replayService = new SseReplayService(replayStore);

  @BeforeEach
  void resetStore() {
    replayStore.clear();
  }

  @Test
  @DisplayName("Last-Event-ID replays only greater numeric sequences in order")
  void lastEventIdReplaysOnlyGreaterSequencesInOrder() {
    replayStore.save(
        event(
            901L,
            "40000000-0000-4000-8000-000000000901",
            "PATH_APPENDED",
            "30000000-0000-4000-8000-000000000501",
            "RECORDING",
            7L));
    replayStore.save(
        event(
            902L,
            "40000000-0000-4000-8000-000000000902",
            "MARKER_CREATED",
            "50000000-0000-4000-8000-000000000801",
            "REQUESTED",
            1L));

    var frames = replayService.replayAfter(INCIDENT_ID, "900");

    assertThat(frames).extracting(SseEventFrame::id).containsExactly("901", "902");
    assertThat(frames)
        .extracting(SseEventFrame::event)
        .containsExactly("PATH_APPENDED", "MARKER_CREATED");

    String firstFrame = SseEventFrameFormatter.format(frames.get(0));
    assertThat(firstFrame).contains("id:901");
    assertThat(firstFrame).contains("event:PATH_APPENDED");
    assertThat(firstFrame).contains("\"eventId\":\"40000000-0000-4000-8000-000000000901\"");
    assertThat(firstFrame).contains("\"incidentId\":\"10000000-0000-4000-8000-000000000001\"");
    assertThat(firstFrame).contains("\"type\":\"PATH_APPENDED\"");
    assertThat(firstFrame).contains("\"payloadFormatVersion\":1");
    assertThat(firstFrame).contains("\"occurredAt\":\"2026-05-08T00:00:00Z\"");
    assertThat(firstFrame).contains("\"id\":\"30000000-0000-4000-8000-000000000501\"");
    assertThat(firstFrame).contains("\"status\":\"RECORDING\"");
    assertThat(firstFrame).contains("\"version\":7");
  }

  @Test
  @DisplayName("duplicate eventId is appended and replayed once")
  void duplicateEventIdIsNotReplayedTwice() {
    var eventId = UUID.fromString("40000000-0000-4000-8000-000000000701");
    var first = EventStreamTestFixtures.publishRequest(eventId, INCIDENT_ID, "PATH_APPENDED");
    var duplicate = EventStreamTestFixtures.publishRequest(eventId, INCIDENT_ID, "PATH_APPENDED");

    var firstAppend = replayStore.append(UUID.randomUUID(), first);
    var secondAppend = replayStore.append(UUID.randomUUID(), duplicate);

    assertThat(secondAppend.eventId()).isEqualTo(firstAppend.eventId());
    assertThat(secondAppend.replaySequence()).isEqualTo(firstAppend.replaySequence());
    assertThat(secondAppend.isNew()).isFalse();
    assertThat(replayService.replayAfter(INCIDENT_ID, "0")).hasSize(1);
  }

  private static SseReplayEvent event(
      long sequence, String eventId, String type, String payloadId, String status, long version) {
    return SseReplayEvent.active(
        UUID.fromString("80000000-0000-4000-8000-%012d".formatted(sequence)),
        UUID.fromString("70000000-0000-4000-8000-%012d".formatted(sequence)),
        INCIDENT_ID,
        sequence,
        EventStreamTestFixtures.publishRequest(
            UUID.fromString(eventId), INCIDENT_ID, type, payloadId, status, version),
        EventStreamTestFixtures.CREATED_AT);
  }
}
