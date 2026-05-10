package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.eventhub.stream.InMemorySseReplayEventStore;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayService;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.eventhub.stream.SseStreamSessionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T07C incident terminal SSE release and replay stop")
class SseIncidentClosureReplayStopTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID PATH_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000000901");
  private static final UUID CLOSED_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000000912");
  private static final UUID PURGED_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000000913");

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
  @DisplayName("INCIDENT_CLOSED is delivered once and then releases the incident stream")
  void incidentClosedDispatchReleasesIncidentStream() {
    var sink = new CapturingSink();
    sessionRegistry.register(INCIDENT_ID, sink);

    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000000912"),
        EventStreamTestFixtures.publishRequest(CLOSED_EVENT_ID, INCIDENT_ID, "INCIDENT_CLOSED"));

    assertThat(sink.frames()).extracting(SseEventFrame::event).containsExactly("INCIDENT_CLOSED");
    assertThat(sink.closed()).isTrue();
    assertThat(sessionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("INCIDENT_PURGED removes old replay rows so reconnect does not receive closed incident data")
  void incidentPurgedStopsOldReplayData() {
    replayStore.save(
        SseReplayEvent.active(
            UUID.fromString("80000000-0000-4000-8000-000000000901"),
            UUID.fromString("70000000-0000-4000-8000-000000000901"),
            INCIDENT_ID,
            901L,
            EventStreamTestFixtures.publishRequest(PATH_EVENT_ID, INCIDENT_ID, "PATH_APPENDED"),
            EventStreamTestFixtures.CREATED_AT));

    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000000912"),
        EventStreamTestFixtures.publishRequest(CLOSED_EVENT_ID, INCIDENT_ID, "INCIDENT_CLOSED"));
    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000000913"),
        EventStreamTestFixtures.publishRequest(PURGED_EVENT_ID, INCIDENT_ID, "INCIDENT_PURGED"));

    assertThat(replayStore.findByIncidentId(INCIDENT_ID)).isEmpty();
    assertThatThrownBy(() -> replayService.replayAfter(INCIDENT_ID, null))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThatThrownBy(() -> replayService.replayAfter(INCIDENT_ID, "901"))
        .isInstanceOf(GoneRefetchRequiredException.class);
  }

  private static final class CapturingSink implements SseLiveEventSink {

    private final List<SseEventFrame> frames = new ArrayList<>();
    private boolean closed;

    @Override
    public void send(SseEventFrame frame) {
      frames.add(frame);
    }

    @Override
    public void close() {
      closed = true;
    }

    List<SseEventFrame> frames() {
      return frames;
    }

    boolean closed() {
      return closed;
    }
  }
}
