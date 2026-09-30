package com.surimap.api.service.sse;

import com.surimap.global.sse.ServerSentEventJobService;
import com.surimap.global.sse.ServerSentEventMessage;
import com.surimap.global.sse.ServerSentEventRefetchRequiredException;
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
public class ServerSentEventHistoryService {

  // 로컬 전환 검증용 초기값이다. 연결 수·복구 지연을 측정한 뒤 조정한다.
  private static final int HISTORY_PAGE_SIZE = 100;
  private final ServerSentEventJobService jobService;

  public HistoryPage getFirstPageAfter(UUID incidentId, String lastEventId) {
    long cursor = parseCursor(lastEventId);
    try {
      long throughSequence = jobService.getHistoryEndSequence(incidentId);
      if (cursor > throughSequence) {
        throw new ServerSentEventRefetchRequiredException();
      }
      if (cursor > 0) {
        jobService.validateHistoryContinuity(incidentId, cursor, throughSequence);
      }
      return getPage(incidentId, cursor, throughSequence, cursor > 0);
    } catch (ServerSentEventRefetchRequiredException exception) {
      return getTerminalPage(incidentId, cursor);
    }
  }

  public HistoryPage getNextPage(UUID incidentId, HistoryPage previous) {
    if (!previous.hasMore()) {
      throw new IllegalArgumentException("SSE history has no next page");
    }
    long cursor =
        Long.parseLong(previous.getMessages().get(previous.getMessages().size() - 1).getId());
    try {
      return getPage(incidentId, cursor, previous.getThroughSequence(), previous.isResumed());
    } catch (ServerSentEventRefetchRequiredException exception) {
      return getTerminalPage(incidentId, cursor);
    }
  }

  private long parseCursor(String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return 0L;
    }
    try {
      long parsed = Long.parseLong(lastEventId);
      if (parsed < 0) {
        throw new ServerSentEventRefetchRequiredException();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new ServerSentEventRefetchRequiredException();
    }
  }

  private HistoryPage getPage(UUID incidentId, long cursor, long throughSequence, boolean resumed) {
    var jobs =
        resumed
            ? jobService.getContiguousHistoryPage(
                incidentId, cursor, throughSequence, HISTORY_PAGE_SIZE)
            : jobService.getRetainedHistoryPage(
                incidentId, cursor, throughSequence, HISTORY_PAGE_SIZE);
    return HistoryPage.builder()
        .messages(jobs.stream().map(ServerSentEventMessage::from).toList())
        .throughSequence(throughSequence)
        .resumed(resumed)
        .build();
  }

  private HistoryPage getTerminalPage(UUID incidentId, long cursor) {
    var closed = jobService.getIncidentClosedEvent(incidentId);
    if (cursor > closed.getServerSentEventSequence()) {
      throw new ServerSentEventRefetchRequiredException();
    }
    return HistoryPage.builder()
        .messages(
            cursor < closed.getServerSentEventSequence()
                ? List.of(ServerSentEventMessage.from(closed))
                : List.of())
        .throughSequence(closed.getServerSentEventSequence())
        .terminalReached(true)
        .build();
  }

  @Getter
  @Builder
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class HistoryPage {
    private List<ServerSentEventMessage> messages;
    private long throughSequence;
    private boolean terminalReached;
    private boolean resumed;

    public boolean hasMore() {
      return !terminalReached
          && !messages.isEmpty()
          && Long.parseLong(messages.get(messages.size() - 1).getId()) < throughSequence;
    }
  }
}
