package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.stream.InMemorySseReplayEventStore;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseReplayService;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.eventhub.stream.SseStreamSessionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T07A live SSE fanout")
class LiveSseFanoutRedTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000901");
  private static final UUID DISPATCH_JOB_ID =
      UUID.fromString("70000000-0000-4000-8000-000000000901");

  private InMemorySseReplayEventStore replayStore;
  private SseStreamSessionRegistry sessionRegistry;
  private SseStreamService streamService;
  private CapturingSink sink;

  @BeforeEach
  void setUp() {
    replayStore = new InMemorySseReplayEventStore();
    sessionRegistry = new SseStreamSessionRegistry();
    streamService =
        new SseStreamService(new SseReplayService(replayStore), replayStore, sessionRegistry);
    sink = new CapturingSink();
  }

  @Test
  @DisplayName("live dispatch appends durable replay row before sending to connected emitter")
  void liveDispatchSendsToConnectedSseEmitterAfterReplayAppend() {
    replayStore.save(
        SseReplayEvent.active(
            UUID.fromString("80000000-0000-4000-8000-000000000900"),
            UUID.fromString("70000000-0000-4000-8000-000000000900"),
            INCIDENT_ID,
            900L,
            EventStreamTestFixtures.publishRequest(
                UUID.fromString("40000000-0000-4000-8000-000000000900"),
                INCIDENT_ID,
                "PATH_APPENDED"),
            EventStreamTestFixtures.CREATED_AT));
    sessionRegistry.register(INCIDENT_ID, sink);

    streamService.dispatchLive(
        DISPATCH_JOB_ID,
        EventStreamTestFixtures.publishRequest(
            EVENT_ID,
            INCIDENT_ID,
            "PATH_APPENDED",
            "30000000-0000-4000-8000-000000000501",
            "RECORDING",
            7L));

    assertThat(sink.frames()).hasSize(1);
    assertThat(replayStore.findByEventId(EVENT_ID)).isPresent();

    SseEventFrame frame = sink.frames().get(0);
    SseReplayEventStore.ReplayAppend append = sink.appendAtSend().get(0);
    assertThat(append.replaySequence()).isEqualTo(901L);
    assertThat(frame.id()).isEqualTo("901");
    assertThat(frame.event()).isEqualTo("PATH_APPENDED");
    assertThat(frame.data().eventId()).isEqualTo(EVENT_ID);
    assertThat(frame.data().payload().get("id")).isEqualTo("30000000-0000-4000-8000-000000000501");
    assertThat(frame.data().payload().get("status")).isEqualTo("RECORDING");
    assertThat(frame.data().payload().get("version")).isEqualTo(7L);
  }

  private final class CapturingSink implements SseLiveEventSink {

    private final List<SseEventFrame> frames = new ArrayList<>();
    private final List<SseReplayEventStore.ReplayAppend> appendAtSend = new ArrayList<>();

    @Override
    public void send(SseEventFrame frame) {
      appendAtSend.add(replayStore.findByEventId(frame.data().eventId()).orElseThrow());
      frames.add(frame);
    }

    List<SseEventFrame> frames() {
      return frames;
    }

    List<SseReplayEventStore.ReplayAppend> appendAtSend() {
      return appendAtSend;
    }
  }
}
