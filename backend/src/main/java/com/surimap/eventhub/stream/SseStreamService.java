package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import java.io.IOException;
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
    var replay = replayService.replayResultAfter(incidentId, lastEventId);
    var emitter = new SseEmitter(0L);
    var sink = new SseEmitterLiveEventSink(emitter);
    if (replay.terminalReached()) {
      replay.frames().forEach(sink::send);
      sink.close();
      return emitter;
    }

    AutoCloseable registration = connectionRegistry.registerForIncident(incidentId, sink);

    emitter.onCompletion(() -> closeQuietly(registration));
    emitter.onTimeout(() -> closeQuietly(registration));
    emitter.onError(ignored -> closeQuietly(registration));

    try {
      sendOpenComment(emitter);
      replay.frames().forEach(sink::send);
    } catch (RuntimeException exception) {
      closeQuietly(registration);
      throw exception;
    }
    return emitter;
  }

  public SseEmitter openAccountStream(UUID accountId) {
    var emitter = new SseEmitter(0L);
    var sink = new SseEmitterLiveEventSink(emitter);
    AutoCloseable registration = connectionRegistry.registerForAccount(accountId, sink);

    emitter.onCompletion(() -> closeQuietly(registration));
    emitter.onTimeout(() -> closeQuietly(registration));
    emitter.onError(ignored -> closeQuietly(registration));

    sendOpenComment(emitter);
    return emitter;
  }

  public SseReplayEventStore.ReplayAppend dispatchLive(
      UUID eventDispatchJobId, PublishRequest request) {
    var append = replayEventStore.append(eventDispatchJobId, request);
    if (append.isNew()) {
      SseEventFrame frame = replayService.frameOf(append.event());
      connectionRegistry.sendToIncident(request.incidentId(), frame);
      assignedAccountIds(request)
          .forEach(accountId -> connectionRegistry.sendToAccount(accountId, frame));
    }
    if (append.isNew() && INCIDENT_CLOSED.equals(request.type())) {
      connectionRegistry.closeIncidentConnections(request.incidentId());
    }
    if (append.isNew() && INCIDENT_PURGED.equals(request.type())) {
      connectionRegistry.closeIncidentConnections(request.incidentId());
      replayEventStore.purgeIncident(request.incidentId());
    }
    return append;
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

  private void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception ignored) {
      // Closing an already completed SSE session is idempotent for the registry.
    }
  }

  private void sendOpenComment(SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().comment("connected"));
    } catch (IOException exception) {
      emitter.completeWithError(exception);
      throw new IllegalStateException("failed to open SSE stream", exception);
    }
  }
}
