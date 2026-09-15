package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.stream.EventStreamConfig;
import com.surimap.eventhub.stream.GoneRefetchRequiredException;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseReplayEvent;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseReplayService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = EventStreamConfig.class)
class SseReplayServiceTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");

  @Autowired private SseReplayService replayService;
  @Autowired private SseReplayEventStore replayStore;

  @BeforeEach
  @AfterEach
  void clearReplayStore() {
    replayStore.clear();
  }

  @Test
  @DisplayName("이벤트가 역순으로 저장돼 있어도, 순번이 작은 것부터 재전송한다")
  void replay_after_out_of_order_storage_returns_events_in_sequence_order() {
    // given: 순번 902를 먼저, 901을 나중에 저장했다.
    replayStore.save(createStoredEvent(902L, "40000000-0000-4000-8000-000000000902"));
    replayStore.save(createStoredEvent(901L, "40000000-0000-4000-8000-000000000901"));

    // when: 순번 900 이후의 이벤트를 요청한다.
    var frames = replayService.replayAfter(INCIDENT_ID, "900");

    // then: 저장 순서가 아니라 이벤트 순번에 따라 반환한다.
    assertThat(frames).extracting(SseEventFrame::id).containsExactly("901", "902");
  }

  @Test
  @DisplayName("이어받을 순번이 누락되면 재조회를 요구하고, 마지막 순번 없는 요청에는 남은 이벤트를 반환한다")
  void replay_with_missing_sequence_requires_refetch_and_fresh_replay_returns_retained_events() {
    // given: 공식 fixture처럼 44와 46만 저장해 45가 누락돼 있다.
    replayStore.save(createStoredEvent(44L, "40000000-0000-4000-8000-000000000044"));
    replayStore.save(createStoredEvent(46L, "40000000-0000-4000-8000-000000000046"));

    // when / then: 44 이후를 이어받으려 하면 누락을 알리는 예외를 반환한다.
    assertThatThrownBy(() -> replayService.replayAfter(INCIDENT_ID, "44"))
        .isInstanceOf(GoneRefetchRequiredException.class);

    // when: 마지막 수신 순번 없이 다시 요청한다.
    var frames = replayService.replayAfter(INCIDENT_ID, null);

    // then: 현재 보관 중인 두 이벤트를 순서대로 반환한다.
    assertThat(frames).extracting(SseEventFrame::id).containsExactly("44", "46");
  }

  private static SseReplayEvent createStoredEvent(long sequence, String eventId) {
    return SseReplayEvent.active(
        UUID.fromString("80000000-0000-4000-8000-%012d".formatted(sequence)),
        UUID.fromString("70000000-0000-4000-8000-%012d".formatted(sequence)),
        INCIDENT_ID,
        sequence,
        EventStreamTestFixtures.publishRequest(
            UUID.fromString(eventId), INCIDENT_ID, "PATH_APPENDED"),
        EventStreamTestFixtures.CREATED_AT);
  }
}
