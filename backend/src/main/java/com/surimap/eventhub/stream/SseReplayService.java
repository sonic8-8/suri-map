package com.surimap.eventhub.stream;

import com.surimap.eventhub.adapter.EventDispatchJobService;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SseReplayService {

  // 로컬 전환 검증용 초기값이다. 연결 수·복구 지연을 측정한 뒤 조정한다.
  private static final int REPLAY_PAGE_SIZE = 100;
  private final EventDispatchJobService jobService;

  public ReplayResult replayResultAfter(UUID incidentId, String lastEventId) {
    long cursor = parseCursor(lastEventId);
    try {
      long throughSequence = jobService.getSseReplayEndSequence(incidentId);
      if (cursor > throughSequence) {
        throw new GoneRefetchRequiredException();
      }
      if (cursor > 0) {
        jobService.validateSseReplayContinuity(incidentId, cursor, throughSequence);
      }
      return readPage(incidentId, cursor, throughSequence, cursor > 0);
    } catch (GoneRefetchRequiredException exception) {
      return readTerminalResult(incidentId, cursor);
    }
  }

  public ReplayResult replayNextPage(UUID incidentId, ReplayResult previous) {
    if (!previous.hasMore()) {
      throw new IllegalArgumentException("SSE replay has no next page");
    }
    long cursor = Long.parseLong(previous.getFrames().get(previous.getFrames().size() - 1).id());
    try {
      return readPage(incidentId, cursor, previous.getThroughSequence(), previous.isResumed());
    } catch (GoneRefetchRequiredException exception) {
      return readTerminalResult(incidentId, cursor);
    }
  }

  private long parseCursor(String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return 0L;
    }
    try {
      long parsed = Long.parseLong(lastEventId);
      if (parsed < 0) {
        throw new GoneRefetchRequiredException();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new GoneRefetchRequiredException();
    }
  }

  private ReplayResult readPage(
      UUID incidentId, long cursor, long throughSequence, boolean resumed) {
    var jobs =
        resumed
            ? jobService.readSseReplayPage(incidentId, cursor, throughSequence, REPLAY_PAGE_SIZE)
            : jobService.readRetainedSseReplayPage(
                incidentId, cursor, throughSequence, REPLAY_PAGE_SIZE);
    return ReplayResult.builder()
        .frames(jobs.stream().map(SseEventFrame::from).toList())
        .throughSequence(throughSequence)
        .resumed(resumed)
        .build();
  }

  private ReplayResult readTerminalResult(UUID incidentId, long cursor) {
    var closed = jobService.readIncidentClosedEvent(incidentId);
    if (cursor > closed.getSseSequence()) {
      throw new GoneRefetchRequiredException();
    }
    return ReplayResult.builder()
        .frames(cursor < closed.getSseSequence() ? List.of(SseEventFrame.from(closed)) : List.of())
        .throughSequence(closed.getSseSequence())
        .terminalReached(true)
        .build();
  }

  @Getter
  @Builder
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class ReplayResult {
    private List<SseEventFrame> frames;
    private long throughSequence;
    private boolean terminalReached;
    private boolean resumed;

    public boolean hasMore() {
      return !terminalReached
          && !frames.isEmpty()
          && Long.parseLong(frames.get(frames.size() - 1).id()) < throughSequence;
    }
  }
}
