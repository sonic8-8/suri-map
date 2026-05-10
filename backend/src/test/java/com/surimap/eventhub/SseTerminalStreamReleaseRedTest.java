package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.eventhub.stream.InMemorySseReplayEventStore;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayService;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.eventhub.stream.SseStreamSessionRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T07C incident terminal SSE stream release")
class SseTerminalStreamReleaseRedTest {

  private static final UUID CLOSED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000012");
  private static final UUID PURGED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000013");
  private static final UUID TERMINAL_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000001212");
  private static final UUID PURGE_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000001313");
  private static final Instant OCCURRED_AT = Instant.parse("2026-05-08T00:00:00Z");

  private InMemorySseReplayEventStore replayStore;
  private SseReplayService replayService;
  private SseStreamSessionRegistry sessionRegistry;
  private SseStreamService streamService;

  @BeforeEach
  void setUp() {
    replayStore = new InMemorySseReplayEventStore();
    replayService = new SseReplayService(replayStore);
    sessionRegistry = new SseStreamSessionRegistry();
    streamService = new SseStreamService(replayService, replayStore, sessionRegistry);
  }

  @Test
  @DisplayName("INCIDENT_CLOSED sends terminal frame and releases active incident sinks")
  void incidentClosedReleasesActiveIncidentStream() {
    replayStore.save(savedEvent(CLOSED_INCIDENT_ID, 1211L, "PATH_APPENDED"));
    var sink = new CapturingSink();
    sessionRegistry.register(CLOSED_INCIDENT_ID, sink);

    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000001212"),
        terminalRequest(TERMINAL_EVENT_ID, CLOSED_INCIDENT_ID, "INCIDENT_CLOSED", "CLOSED", 12L));

    assertThat(sink.frames()).extracting(SseEventFrame::event).containsExactly("INCIDENT_CLOSED");
    assertThat(sink.frames()).extracting(SseEventFrame::id).containsExactly("1212");
    assertThat(sessionRegistry.sinks(CLOSED_INCIDENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("Last-Event-ID before INCIDENT_CLOSED replays terminal event without live tail")
  void closedIncidentReplayStopsAtTerminalEvent() {
    replayStore.save(savedEvent(CLOSED_INCIDENT_ID, 1211L, "PATH_APPENDED"));
    replayStore.save(savedEvent(CLOSED_INCIDENT_ID, 1212L, "INCIDENT_CLOSED"));

    var replay = replayService.replayResultAfter(CLOSED_INCIDENT_ID, "1211");

    assertThat(replay.frames()).extracting(SseEventFrame::event).containsExactly("INCIDENT_CLOSED");
    assertThat(replay.terminalReached()).isTrue();

    streamService.openStream(CLOSED_INCIDENT_ID, "1212");

    assertThat(sessionRegistry.sinks(CLOSED_INCIDENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("INCIDENT_PURGED removes incident replay data and later replay requires refetch")
  void incidentPurgedStopsOldReplay() {
    replayStore.save(savedEvent(PURGED_INCIDENT_ID, 1301L, "INCIDENT_CLOSED"));

    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000001313"),
        terminalRequest(PURGE_EVENT_ID, PURGED_INCIDENT_ID, "INCIDENT_PURGED", "PURGED", 13L));

    assertThatThrownBy(() -> replayService.replayAfter(PURGED_INCIDENT_ID, "1301"))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThatThrownBy(
            () ->
                streamService.dispatchLive(
                    UUID.fromString("70000000-0000-4000-8000-000000001314"),
                    terminalRequest(
                        UUID.fromString("40000000-0000-4000-8000-000000001314"),
                        PURGED_INCIDENT_ID,
                        "PATH_APPENDED",
                        "RECORDING",
                        14L)))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThat(replayStore.findByIncidentId(PURGED_INCIDENT_ID)).isEmpty();
  }

  private static SseReplayEvent savedEvent(UUID incidentId, long sequence, String type) {
    return SseReplayEvent.active(
        UUID.fromString("80000000-0000-4000-8000-%012d".formatted(sequence)),
        UUID.fromString("70000000-0000-4000-8000-%012d".formatted(sequence)),
        incidentId,
        sequence,
        terminalRequest(
            UUID.fromString("40000000-0000-4000-8000-%012d".formatted(sequence)),
            incidentId,
            type,
            type,
            sequence),
        OCCURRED_AT);
  }

  private static PublishRequest terminalRequest(
      UUID eventId, UUID incidentId, String type, String status, long version) {
    return new PublishRequest(
        eventId,
        incidentId,
        type,
        1,
        "incident",
        incidentId,
        OCCURRED_AT,
        Map.of("id", incidentId.toString(), "status", status, "version", version));
  }

  private static final class CapturingSink implements SseLiveEventSink {

    private final List<SseEventFrame> frames = new ArrayList<>();

    @Override
    public void send(SseEventFrame frame) {
      frames.add(frame);
    }

    List<SseEventFrame> frames() {
      return frames;
    }
  }
}
