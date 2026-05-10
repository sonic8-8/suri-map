package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.stream.GoneRefetchRequiredException;
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

@DisplayName("L2-T07B SSE failure injection")
class SseFailureInjectionTest {

  // S4.json duplicate_event_dedupe fixture
  private static final UUID INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID DEDUPE_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000000701");

  // S4.json replay_gap_refetch_required fixture
  // lastEventId "44", missingSequence 45, nextRetainedSequence 46
  private static final long GAP_LAST_SEQ = 44L;
  private static final long GAP_MISSING_SEQ = 45L;
  private static final long GAP_NEXT_RETAINED_SEQ = 46L;

  private InMemorySseReplayEventStore replayStore;
  private SseStreamSessionRegistry sessionRegistry;
  private SseStreamService streamService;
  private SseReplayService replayService;

  @BeforeEach
  void setUp() {
    replayStore = new InMemorySseReplayEventStore();
    replayService = new SseReplayService(replayStore);
    sessionRegistry = new SseStreamSessionRegistry();
    streamService = new SseStreamService(replayService, replayStore, sessionRegistry);
  }

  // ── 테스트 1 ─────────────────────────────────────────────────────────────

  /**
   * RED: dispatchLive()를 같은 eventId로 두 번 호출하면 현재 구현은 sessionRegistry.send()를
   * 두 번 실행하므로 sink에 프레임이 2개 전달된다. 올바른 동작은 1개여야 한다.
   *
   * <p>S4.json duplicate_event_dedupe.eventId = 40000000-0000-4000-8000-000000000701
   */
  @Test
  @DisplayName("같은 eventId로 dispatchLive 두 번 호출 시 live sink에 프레임이 1번만 전달돼야 한다")
  void duplicateDispatch_onlyOneFrameSentToLiveSink() {
    var sink = new CapturingSink();
    sessionRegistry.register(INCIDENT_ID, sink);

    var request =
        EventStreamTestFixtures.publishRequest(DEDUPE_EVENT_ID, INCIDENT_ID, "PATH_APPENDED");

    // 같은 eventId, 서로 다른 eventDispatchJobId로 두 번 호출
    UUID firstJobId = UUID.fromString("70000000-0000-4000-8000-000000000701");
    UUID secondJobId = UUID.fromString("70000000-0000-4000-8000-000000000702");

    streamService.dispatchLive(firstJobId, request);
    streamService.dispatchLive(secondJobId, request);

    // replay store에는 dedupe로 1개만 존재해야 한다 (이미 올바름)
    assertThat(replayStore.findByEventId(DEDUPE_EVENT_ID)).isPresent();
    assertThat(replayStore.findByIncidentId(INCIDENT_ID)).hasSize(1);

    // RED: 현재 구현은 send()를 2번 호출하므로 이 단언은 실패한다
    assertThat(sink.frames()).hasSize(1);
  }

  // ── 테스트 2 ─────────────────────────────────────────────────────────────

  /**
   * GREEN: ConcurrentSkipListMap이 key(replaySequence) 기준 오름차순을 보장하므로
   * 역순으로 save()해도 replay는 ascending order로 반환된다.
   */
  @Test
  @DisplayName("역순으로 저장된 이벤트도 replay는 오름차순(seq 901 → 902)으로 반환된다")
  void outOfOrderSave_replayedInAscendingSequence() {
    // seq 902를 먼저, seq 901을 나중에 저장 (역순)
    replayStore.save(savedEvent(902L, "40000000-0000-4000-8000-000000000902"));
    replayStore.save(savedEvent(901L, "40000000-0000-4000-8000-000000000901"));

    List<SseEventFrame> frames = replayService.replayAfter(INCIDENT_ID, "900");

    assertThat(frames).extracting(SseEventFrame::id).containsExactly("901", "902");
  }

  // ── 테스트 3 ─────────────────────────────────────────────────────────────

  /**
   * GREEN: seq 44, 46 저장 후 seq 45가 누락된 상태에서 lastEventId="44"로 연결하면
   * GoneRefetchRequiredException이 발생하고, fresh connect(lastEventId=null)는 성공한다.
   *
   * <p>S4.json replay_gap_refetch_required:
   *   lastEventId="44", missingSequence=45, nextRetainedSequence=46
   */
  @Test
  @DisplayName("seq gap 발생 시 GoneRefetchRequiredException, fresh connect는 남은 이벤트 전체 반환")
  void goneRefetchRequired_freshReconnectServesRemainingEvents() {
    // seq 44 저장, seq 45 누락, seq 46 저장
    replayStore.save(savedEvent(GAP_LAST_SEQ, "40000000-0000-4000-8000-000000000044"));
    replayStore.save(savedEvent(GAP_NEXT_RETAINED_SEQ, "40000000-0000-4000-8000-000000000046"));

    // gap 감지 → GoneRefetchRequiredException
    assertThatThrownBy(() -> replayService.replayAfter(INCIDENT_ID, String.valueOf(GAP_LAST_SEQ)))
        .isInstanceOf(GoneRefetchRequiredException.class);

    // fresh connect (lastEventId=null) → seq 44, 46 모두 반환
    List<SseEventFrame> frames = replayService.replayAfter(INCIDENT_ID, null);
    assertThat(frames).extracting(SseEventFrame::id)
        .containsExactly(
            String.valueOf(GAP_LAST_SEQ),
            String.valueOf(GAP_NEXT_RETAINED_SEQ));
  }

  // ── 헬퍼 ─────────────────────────────────────────────────────────────────

  private SseReplayEvent savedEvent(long sequence, String eventId) {
    return SseReplayEvent.active(
        UUID.fromString("80000000-0000-4000-8000-%012d".formatted(sequence)),
        UUID.fromString("70000000-0000-4000-8000-%012d".formatted(sequence)),
        INCIDENT_ID,
        sequence,
        EventStreamTestFixtures.publishRequest(
            UUID.fromString(eventId), INCIDENT_ID, "PATH_APPENDED"),
        EventStreamTestFixtures.CREATED_AT);
  }

  // ── 내부 클래스 ───────────────────────────────────────────────────────────

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
