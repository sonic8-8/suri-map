package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.stream.EventStreamConfig;
import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.eventhub.stream.SseConnectionRegistry;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseReplayService;
import com.surimap.eventhub.stream.SseStreamService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = EventStreamConfig.class)
class SseStreamServiceTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID CLOSED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000012");
  private static final UUID PURGED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000013");

  @Autowired private SseStreamService streamService;
  @Autowired private SseReplayService replayService;
  @Autowired private SseReplayEventStore replayStore;
  @Autowired private SseConnectionRegistry connectionRegistry;

  @BeforeEach
  void clearReplayStore() {
    replayStore.clear();
  }

  @AfterEach
  void closeConnections() {
    connectionRegistry.closeIncidentConnections(INCIDENT_ID);
    connectionRegistry.closeIncidentConnections(CLOSED_INCIDENT_ID);
    connectionRegistry.closeIncidentConnections(PURGED_INCIDENT_ID);
    replayStore.clear();
  }

  @Test
  @DisplayName("새 이벤트를 전송하면, 재전송할 수 있도록 먼저 저장하고 같은 순번·내용을 전달한다")
  void dispatch_live_saves_event_before_sending_matching_sequence_and_payload() {
    // given: 순번 900까지 저장된 사건에 전송 대상이 등록돼 있다.
    UUID eventId = UUID.fromString("40000000-0000-4000-8000-000000000901");
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
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(INCIDENT_ID, connection);

    // when: 다음 경로 추가 이벤트를 전송한다.
    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000000901"),
        EventStreamTestFixtures.publishRequest(
            eventId,
            INCIDENT_ID,
            "PATH_APPENDED",
            "30000000-0000-4000-8000-000000000501",
            "RECORDING",
            7L),
        901L);

    // then: 전송 순간에 이미 저장돼 있으며, 순번과 원래 내용이 그대로 전달된다.
    assertThat(connection.getFrames()).hasSize(1);
    assertThat(replayStore.findByEventId(eventId)).isPresent();
    assertThat(connection.getStoredEventsAtSend().get(0).replaySequence()).isEqualTo(901L);
    var frame = connection.getFrames().get(0);
    assertThat(frame.id()).isEqualTo("901");
    assertThat(frame.event()).isEqualTo("PATH_APPENDED");
    assertThat(frame.data().eventId()).isEqualTo(eventId);
    assertThat(frame.data().payload().get("id")).isEqualTo("30000000-0000-4000-8000-000000000501");
    assertThat(frame.data().payload().get("status")).isEqualTo("RECORDING");
    assertThat(frame.data().payload().get("version")).isEqualTo(7L);
  }

  @Test
  @DisplayName("같은 작업의 전송을 재시도하면 이력은 하나로 유지하고 같은 순번으로 다시 보낸다")
  void dispatch_live_retry_preserves_single_history_and_sends_same_sequence_again() {
    // given: 같은 작업을 재시도할 수 있는 전송 대상이 등록돼 있다.
    UUID eventId = UUID.fromString("40000000-0000-4000-8000-000000000701");
    var event = EventStreamTestFixtures.publishRequest(eventId, INCIDENT_ID, "PATH_APPENDED");
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(INCIDENT_ID, connection);

    // when: DB에서 확정한 같은 순번으로 다시 전송한다.
    UUID jobId = UUID.fromString("70000000-0000-4000-8000-000000000701");
    streamService.dispatchLive(jobId, event, 1L);
    streamService.dispatchLive(jobId, event, 1L);

    // then: 저장된 이력은 하나이며 전송은 같은 순번으로 다시 시도한다.
    assertThat(replayStore.findByEventId(eventId)).isPresent();
    assertThat(replayStore.findByIncidentId(INCIDENT_ID)).hasSize(1);
    assertThat(connection.getFrames()).extracting(SseEventFrame::id).containsExactly("1", "1");
  }

  @Test
  @DisplayName("이전 이벤트가 있는 사건을 종료하면, 다음 순번으로 종료를 알리고 연결을 제거한다")
  void dispatch_closed_incident_after_previous_event_sends_next_sequence_and_removes_connection() {
    // given: 순번 1211까지 기록된 사건에 연결이 등록돼 있다.
    replayStore.save(createStoredIncidentEvent(CLOSED_INCIDENT_ID, 1211L, "PATH_APPENDED"));
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(CLOSED_INCIDENT_ID, connection);

    // when: 사건 종료를 전송한다.
    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000001212"),
        createIncidentEvent(
            UUID.fromString("40000000-0000-4000-8000-000000001212"),
            CLOSED_INCIDENT_ID,
            "INCIDENT_CLOSED",
            "CLOSED",
            12L),
        1212L);

    // then: 종료 이벤트만 다음 순번으로 전달하고 연결 등록을 제거한다.
    assertThat(connection.getFrames())
        .extracting(SseEventFrame::event)
        .containsExactly("INCIDENT_CLOSED");
    assertThat(connection.getFrames()).extracting(SseEventFrame::id).containsExactly("1212");
    assertThat(connectionRegistry.sinks(CLOSED_INCIDENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("이전 이벤트가 없는 사건을 종료해도, 종료 알림을 전달하고 연결을 닫는다")
  void
      dispatch_closed_incident_without_previous_event_sends_terminal_event_and_closes_connection() {
    // given: 저장된 이벤트가 없는 사건에 연결이 등록돼 있다.
    var connection = new CapturingSseConnection();
    connectionRegistry.registerForIncident(INCIDENT_ID, connection);

    // when: 사건 종료를 첫 이벤트로 전송한다.
    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000000912"),
        EventStreamTestFixtures.publishRequest(
            UUID.fromString("40000000-0000-4000-8000-000000000912"),
            INCIDENT_ID,
            "INCIDENT_CLOSED"),
        1L);

    // then: 종료 이벤트를 한 번 전달하고 실제 연결과 등록을 모두 정리한다.
    assertThat(connection.getFrames())
        .extracting(SseEventFrame::event)
        .containsExactly("INCIDENT_CLOSED");
    assertThat(connection.isClosed()).isTrue();
    assertThat(connectionRegistry.sinks(INCIDENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("종료 이력이 저장된 사건을 파기하면, 재전송 데이터를 지우고 새 이벤트도 거부한다")
  void dispatch_purged_incident_removes_replay_and_rejects_new_events() {
    // given: 사건 종료 이력이 저장돼 있다.
    replayStore.save(createStoredIncidentEvent(PURGED_INCIDENT_ID, 1301L, "INCIDENT_CLOSED"));

    // when: 사건 파기를 전송한다.
    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000001313"),
        createIncidentEvent(
            UUID.fromString("40000000-0000-4000-8000-000000001313"),
            PURGED_INCIDENT_ID,
            "INCIDENT_PURGED",
            "PURGED",
            13L),
        1302L);

    // then: 과거 이벤트 재전송과 새 이벤트 전송을 모두 거부한다.
    assertThatThrownBy(() -> replayService.replayAfter(PURGED_INCIDENT_ID, "1301"))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThatThrownBy(
            () ->
                streamService.dispatchLive(
                    UUID.fromString("70000000-0000-4000-8000-000000001314"),
                    createIncidentEvent(
                        UUID.fromString("40000000-0000-4000-8000-000000001314"),
                        PURGED_INCIDENT_ID,
                        "PATH_APPENDED",
                        "RECORDING",
                        14L),
                    1303L))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThat(replayStore.findByIncidentId(PURGED_INCIDENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("경로 기록 후 사건을 종료하고 파기하면, 새 연결과 이어받기 모두 과거 데이터를 받지 못한다")
  void dispatch_closed_then_purged_incident_rejects_fresh_and_resumed_replay() {
    // given: 진행 중인 사건에 경로 이벤트가 저장돼 있다.
    replayStore.save(
        SseReplayEvent.active(
            UUID.fromString("80000000-0000-4000-8000-000000000901"),
            UUID.fromString("70000000-0000-4000-8000-000000000901"),
            INCIDENT_ID,
            901L,
            EventStreamTestFixtures.publishRequest(
                UUID.fromString("40000000-0000-4000-8000-000000000901"),
                INCIDENT_ID,
                "PATH_APPENDED"),
            EventStreamTestFixtures.CREATED_AT));

    // when: 같은 사건을 종료한 다음 파기한다.
    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000000912"),
        EventStreamTestFixtures.publishRequest(
            UUID.fromString("40000000-0000-4000-8000-000000000912"),
            INCIDENT_ID,
            "INCIDENT_CLOSED"),
        902L);
    streamService.dispatchLive(
        UUID.fromString("70000000-0000-4000-8000-000000000913"),
        EventStreamTestFixtures.publishRequest(
            UUID.fromString("40000000-0000-4000-8000-000000000913"),
            INCIDENT_ID,
            "INCIDENT_PURGED"),
        903L);

    // then: 저장 데이터가 사라지고, 마지막 순번 유무와 관계없이 재전송을 거부한다.
    assertThat(replayStore.findByIncidentId(INCIDENT_ID)).isEmpty();
    assertThatThrownBy(() -> replayService.replayAfter(INCIDENT_ID, null))
        .isInstanceOf(GoneRefetchRequiredException.class);
    assertThatThrownBy(() -> replayService.replayAfter(INCIDENT_ID, "901"))
        .isInstanceOf(GoneRefetchRequiredException.class);
  }

  private static SseReplayEvent createStoredIncidentEvent(
      UUID incidentId, long sequence, String type) {
    return SseReplayEvent.active(
        UUID.fromString("80000000-0000-4000-8000-%012d".formatted(sequence)),
        UUID.fromString("70000000-0000-4000-8000-%012d".formatted(sequence)),
        incidentId,
        sequence,
        createIncidentEvent(
            UUID.fromString("40000000-0000-4000-8000-%012d".formatted(sequence)),
            incidentId,
            type,
            type,
            sequence),
        EventStreamTestFixtures.CREATED_AT);
  }

  private static PublishRequest createIncidentEvent(
      UUID eventId, UUID incidentId, String type, String status, long version) {
    return new PublishRequest(
        eventId,
        incidentId,
        type,
        1,
        "incident",
        incidentId,
        EventStreamTestFixtures.CREATED_AT,
        Map.of("id", incidentId.toString(), "status", status, "version", version));
  }

  private final class CapturingSseConnection implements SseLiveEventSink {
    private final List<SseEventFrame> frames = new ArrayList<>();
    private final List<SseReplayEventStore.ReplayAppend> storedEventsAtSend = new ArrayList<>();
    private boolean closed;

    @Override
    public void send(SseEventFrame frame) {
      storedEventsAtSend.add(replayStore.findByEventId(frame.data().eventId()).orElseThrow());
      frames.add(frame);
    }

    @Override
    public void close() {
      closed = true;
    }

    List<SseEventFrame> getFrames() {
      return frames;
    }

    List<SseReplayEventStore.ReplayAppend> getStoredEventsAtSend() {
      return storedEventsAtSend;
    }

    boolean isClosed() {
      return closed;
    }
  }
}
