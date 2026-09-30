package com.surimap.api.service.sse;

import com.surimap.global.sse.ServerSentEventConnection;
import com.surimap.global.sse.ServerSentEventConnectionRegistry;
import com.surimap.global.sse.ServerSentEventJobService;
import com.surimap.global.sse.ServerSentEventMessage;
import com.surimap.global.sse.ServerSentEventRefetchRequiredException;
import com.surimap.global.sse.ServerSentEventStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

@Service
@RequiredArgsConstructor
public class ServerSentEventSubscriptionService {

  private static final String INCIDENT_CLOSED = "INCIDENT_CLOSED";
  private static final String INCIDENT_PURGED = "INCIDENT_PURGED";

  private final ServerSentEventHistoryService historyService;
  private final ServerSentEventJobService jobService;
  private final ServerSentEventConnectionRegistry connectionRegistry;

  public DeferredResult<Void> openIncidentStream(UUID incidentId, String lastEventId) {
    // 스트림을 시작하기 전에 재조회가 필요한 요청은 기존 HTTP 409로 거부한다.
    historyService.getFirstPageAfter(incidentId, lastEventId);
    return new ServerSentEventStream(
        response -> startIncidentSubscription(incidentId, lastEventId, response));
  }

  public DeferredResult<Void> openAccountStream(UUID accountId) {
    return new ServerSentEventStream(
        response -> {
          var sender = new ServerSentEventConnection(response, false);
          AutoCloseable registration = connectionRegistry.registerForAccount(accountId, sender);
          if (response.attachRegistration(
              () -> {
                sender.clear();
                registration.close();
              })) {
            sender.start(true, null, null);
          }
        });
  }

  private void startIncidentSubscription(
      UUID incidentId, String lastEventId, ServerSentEventStream response) {
    var sender = new ServerSentEventConnection(response, true);
    AutoCloseable registration = connectionRegistry.registerForIncident(incidentId, sender);
    if (response.attachRegistration(
        () -> {
          sender.clear();
          registration.close();
        })) {
      try {
        // 먼저 등록해 조회 도중 도착한 이벤트를 대기시킨다. 이력과 겹친 순번은 한 번만 보낸다.
        var history = historyService.getFirstPageAfter(incidentId, lastEventId);
        var cursor = new IncidentHistoryCursor(incidentId, history, sender);
        sender.start(
            !history.isTerminalReached(),
            cursor::nextMessage,
            eventType -> validateEventTransmission(incidentId, eventType));
      } catch (ServerSentEventRefetchRequiredException exception) {
        // 조회 중 파기돼도 이미 도착한 종료 알림은 버리지 않는다. 일반 이력 누락은 숨기지 않는다.
        if (!sender.isHistoryStopped()) {
          throw exception;
        }
        sender.start(false, null, null);
      }
    }
  }

  private void validateEventTransmission(UUID incidentId, String eventType) {
    if (!INCIDENT_CLOSED.equals(eventType) && !INCIDENT_PURGED.equals(eventType)) {
      // 대기열에 넣을 때의 OPEN 확인만으로 나중의 쓰기를 허용하지 않는다.
      // 이미 시작한 응답 쓰기를 취소하거나 DB 커밋과 네트워크 쓰기를 원자화하지는 않는다.
      jobService.validateServerSentEventTransmission(incidentId);
    }
  }

  /** 한 페이지만 보유하며 이전 프레임 전송을 마친 뒤 다음 이력을 읽는다. */
  private final class IncidentHistoryCursor {
    private final UUID incidentId;
    private final ServerSentEventConnection connection;
    private ServerSentEventHistoryService.HistoryPage page;
    private int nextMessageIndex;

    private IncidentHistoryCursor(
        UUID incidentId,
        ServerSentEventHistoryService.HistoryPage page,
        ServerSentEventConnection connection) {
      this.incidentId = incidentId;
      this.page = page;
      this.connection = connection;
    }

    private ServerSentEventMessage nextMessage() {
      while (nextMessageIndex == page.getMessages().size()) {
        if (page.isTerminalReached()) {
          connection.close();
          return null;
        }
        if (!page.hasMore()) {
          return null;
        }
        page = historyService.getNextPage(incidentId, page);
        nextMessageIndex = 0;
      }
      return page.getMessages().get(nextMessageIndex++);
    }
  }
}
