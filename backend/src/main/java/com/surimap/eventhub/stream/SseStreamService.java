package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.validation.BaseEventValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseStreamService {

  private static final String INCIDENT_CLOSED = "INCIDENT_CLOSED";
  private static final String INCIDENT_PURGED = "INCIDENT_PURGED";

  private final SseReplayService replayService;
  private final SseReplayEventStore replayEventStore;
  private final SseConnectionRegistry connectionRegistry;

  public SseStreamService(
      SseReplayService replayService,
      SseReplayEventStore replayEventStore,
      SseConnectionRegistry connectionRegistry) {
    this.replayService = Objects.requireNonNull(replayService, "replayService must not be null");
    this.replayEventStore =
        Objects.requireNonNull(replayEventStore, "replayEventStore must not be null");
    this.connectionRegistry =
        Objects.requireNonNull(connectionRegistry, "connectionRegistry must not be null");
  }

  public SseEmitter openStream(UUID incidentId, String lastEventId) {
    // ponytail: 전체 메모리 이력 조회는 임시 유지한다. DB 페이지·실시간 전환 조율로 교체한다.
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

  public SseReplayEventStore.ReplayAppend dispatchLive(
      UUID eventDispatchJobId, PublishRequest request, long sseSequence) {
    BaseEventValidator.validate(request);
    boolean isNew = replayEventStore.findByEventId(request.eventId()).isEmpty();
    var event =
        replayEventStore.save(
            SseReplayEvent.active(
                eventDispatchJobId,
                eventDispatchJobId,
                request.incidentId(),
                sseSequence,
                request,
                Instant.now()));
    SseEventFrame frame = replayService.frameOf(event);
    // 이력 저장 성공은 전송 성공이 아니다. 재시도도 같은 순번으로 전달한다.
    connectionRegistry.sendToIncident(request.incidentId(), frame);
    assignedAccountIds(request)
        .forEach(accountId -> connectionRegistry.sendToAccount(accountId, frame));
    if (INCIDENT_CLOSED.equals(request.type())) {
      connectionRegistry.closeIncidentConnections(request.incidentId());
    }
    if (INCIDENT_PURGED.equals(request.type())) {
      connectionRegistry.closeIncidentConnections(request.incidentId());
      replayEventStore.purgeIncident(request.incidentId());
    }
    return new SseReplayEventStore.ReplayAppend(
        request.eventId(), request.incidentId(), sseSequence, event, isNew);
  }

  private void sendIncidentReplay(UUID incidentId, String lastEventId, SseStreamEmitter emitter) {
    // 컨테이너 작업을 기다리는 동안 추가된 이벤트도 다시 조회한다.
    var replay = replayService.replayResultAfter(incidentId, lastEventId);
    var sink = new SseEmitterLiveEventSink(emitter);
    if (replay.terminalReached()) {
      replay.frames().forEach(sink::send);
      sink.close();
      return;
    }
    AutoCloseable registration = connectionRegistry.registerForIncident(incidentId, sink);
    if (emitter.attachRegistration(registration)) {
      sendOpenComment(emitter);
      replay.frames().forEach(sink::send);
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
