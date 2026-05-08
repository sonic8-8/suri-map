package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseStreamService {

  private final SseReplayService replayService;
  private final SseReplayEventStore replayEventStore;
  private final SseStreamSessionRegistry sessionRegistry;

  public SseStreamService(
      SseReplayService replayService,
      SseReplayEventStore replayEventStore,
      SseStreamSessionRegistry sessionRegistry) {
    this.replayService = Objects.requireNonNull(replayService, "replayService must not be null");
    this.replayEventStore =
        Objects.requireNonNull(replayEventStore, "replayEventStore must not be null");
    this.sessionRegistry =
        Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
  }

  public SseEmitter openStream(UUID incidentId, String lastEventId) {
    var replayFrames = replayService.replayAfter(incidentId, lastEventId);
    var emitter = new SseEmitter(0L);
    var sink = new SseEmitterLiveEventSink(emitter);
    AutoCloseable registration = sessionRegistry.register(incidentId, sink);

    emitter.onCompletion(() -> closeQuietly(registration));
    emitter.onTimeout(() -> closeQuietly(registration));
    emitter.onError(ignored -> closeQuietly(registration));

    replayFrames.forEach(sink::send);
    return emitter;
  }

  public SseReplayEventStore.ReplayAppend dispatchLive(
      UUID eventDispatchJobId, PublishRequest request) {
    var append = replayEventStore.append(eventDispatchJobId, request);
    sessionRegistry.send(request.incidentId(), replayService.frameOf(append.event()));
    return append;
  }

  private void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception ignored) {
      // Closing an already completed SSE session is idempotent for the registry.
    }
  }
}
