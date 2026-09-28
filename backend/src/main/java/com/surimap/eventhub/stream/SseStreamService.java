package com.surimap.eventhub.stream;

import com.surimap.eventhub.adapter.EventDispatchJobService;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.validation.BaseEventValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class SseStreamService {

  private static final String INCIDENT_CLOSED = "INCIDENT_CLOSED";
  private static final String INCIDENT_PURGED = "INCIDENT_PURGED";

  private final SseReplayService replayService;
  private final EventDispatchJobService jobService;
  private final SseConnectionRegistry connectionRegistry;

  public SseEmitter openStream(UUID incidentId, String lastEventId) {
    // 스트림을 시작하기 전에 재조회가 필요한 요청은 기존 HTTP 409로 거부한다.
    replayService.replayResultAfter(incidentId, lastEventId);
    return new SseStreamEmitter(emitter -> sendIncidentReplay(incidentId, lastEventId, emitter));
  }

  public SseEmitter openAccountStream(UUID accountId) {
    return new SseStreamEmitter(
        emitter -> {
          var sink = new SseEmitterLiveEventSink(emitter);
          AutoCloseable registration = connectionRegistry.registerForAccount(accountId, sink);
          if (emitter.attachRegistration(registration)) {
            sendOpenComment(emitter);
          }
        });
  }

  public SseEventFrame dispatchLive(PublishRequest request, long sseSequence) {
    BaseEventValidator.validate(request);
    if (sseSequence <= 0) {
      throw new IllegalArgumentException("sseSequence must be positive");
    }
    boolean terminal =
        INCIDENT_CLOSED.equals(request.type()) || INCIDENT_PURGED.equals(request.type());
    if (!terminal) {
      jobService.getSseReplayEndSequence(request.incidentId());
    }
    var frame = new SseEventFrame(Long.toString(sseSequence), request.type(), request);
    // 이력 저장 성공은 전송 성공이 아니다. 재시도도 같은 순번으로 전달한다.
    connectionRegistry.sendToIncident(request.incidentId(), frame);
    assignedAccountIds(request)
        .forEach(accountId -> connectionRegistry.sendToAccount(accountId, frame));
    if (terminal) {
      connectionRegistry.closeIncidentConnections(request.incidentId());
    }
    return frame;
  }

  private void sendIncidentReplay(UUID incidentId, String lastEventId, SseStreamEmitter emitter) {
    var sink = new SseEmitterLiveEventSink(emitter, true);
    AutoCloseable registration = connectionRegistry.registerForIncident(incidentId, sink);
    if (emitter.attachRegistration(
        () -> {
          sink.discardPendingEvents();
          registration.close();
        })) {
      try {
        // 먼저 등록해 조회 도중 도착한 이벤트를 대기시킨다. 이력과 겹친 순번은 한 번만 보낸다.
        var replay = replayService.replayResultAfter(incidentId, lastEventId);
        if (!replay.isTerminalReached()) {
          sendOpenComment(emitter);
        }
        while (!sink.isReplayStopped()) {
          replay.getFrames().forEach(sink::sendReplay);
          if (replay.isTerminalReached()) {
            sink.close();
            break;
          }
          if (!replay.hasMore() || sink.isReplayStopped()) {
            break;
          }
          replay = replayService.replayNextPage(incidentId, replay);
        }
      } catch (GoneRefetchRequiredException exception) {
        // 조회 중 파기돼도 이미 도착한 종료 알림은 버리지 않는다. 일반 이력 누락은 숨기지 않는다.
        if (!sink.isReplayStopped()) {
          throw exception;
        }
      }
      sink.finishReplay();
    }
  }

  private void sendOpenComment(SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private List<UUID> assignedAccountIds(PublishRequest request) {
    Object value =
        "INCIDENT_CREATED".equals(request.type())
            ? request.payload().get("memberAccountIds")
            : request.payload().get("changedAccountIds");
    if (!(value instanceof List<?> values)) {
      return List.of();
    }
    return values.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(this::parseUuidOrNull)
        .filter(Objects::nonNull)
        .toList();
  }

  private UUID parseUuidOrNull(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
