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
    Long cursor = parseCursor(lastEventId);
    try {
      long throughSequence = jobService.getHistoryEndSequence(incidentId);
      if (cursor == null) {
        // 순번 없는 조회에는 과거 이벤트가 없다. 구독 흐름에서는 getStartEventId로 정해진 순번으로 조회한다.
        return HistoryPage.builder().messages(List.of()).throughSequence(throughSequence).build();
      }
      if (cursor > throughSequence) {
        throw new ServerSentEventRefetchRequiredException();
      }
      jobService.validateHistoryContinuity(incidentId, cursor, throughSequence);
      return getPage(incidentId, cursor, throughSequence);
    } catch (ServerSentEventRefetchRequiredException exception) {
      return getTerminalPage(incidentId, cursor == null ? 0L : cursor);
    }
  }

  /** 순번 없는 연결은 연결을 등록하기 전의 마지막 확정 순번까지 받은 연결로 다룬다. 종료되거나 파기된 사건에는 시작 순번이 없다. */
  public String getStartEventId(UUID incidentId, String lastEventId) {
    if (parseCursor(lastEventId) != null) {
      return lastEventId;
    }
    try {
      return Long.toString(jobService.getHistoryEndSequence(incidentId));
    } catch (ServerSentEventRefetchRequiredException exception) {
      return null;
    }
  }

  public HistoryPage getNextPage(UUID incidentId, HistoryPage previous) {
    if (!previous.hasMore()) {
      throw new IllegalArgumentException("SSE history has no next page");
    }
    long cursor =
        Long.parseLong(previous.getMessages().get(previous.getMessages().size() - 1).getId());
    try {
      return getPage(incidentId, cursor, previous.getThroughSequence());
    } catch (ServerSentEventRefetchRequiredException exception) {
      return getTerminalPage(incidentId, cursor);
    }
  }

  /** 순번이 없으면 null이다. 0은 "0번까지 반영했다"는 정상 순번이다. */
  private Long parseCursor(String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return null;
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

  private HistoryPage getPage(UUID incidentId, long cursor, long throughSequence) {
    var jobs =
        jobService.getContiguousHistoryPage(incidentId, cursor, throughSequence, HISTORY_PAGE_SIZE);
    return HistoryPage.builder()
        .messages(jobs.stream().map(ServerSentEventMessage::from).toList())
        .throughSequence(throughSequence)
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

    public boolean hasMore() {
      return !terminalReached
          && !messages.isEmpty()
          && Long.parseLong(messages.get(messages.size() - 1).getId()) < throughSequence;
    }
  }
}
