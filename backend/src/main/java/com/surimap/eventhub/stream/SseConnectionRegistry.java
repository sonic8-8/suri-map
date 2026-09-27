package com.surimap.eventhub.stream;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SseConnectionRegistry {

  private static final Logger log = LoggerFactory.getLogger(SseConnectionRegistry.class);

  private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> sinksByIncident =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> sinksByAccount =
      new ConcurrentHashMap<>();

  public AutoCloseable registerForIncident(UUID incidentId, SseLiveEventSink sink) {
    return register(sinksByIncident, incidentId, sink);
  }

  public void sendToIncident(UUID incidentId, SseEventFrame frame) {
    sendToRegisteredSinks(sinksByIncident, incidentId, frame);
  }

  public AutoCloseable registerForAccount(UUID accountId, SseLiveEventSink sink) {
    return register(sinksByAccount, accountId, sink);
  }

  public void sendToAccount(UUID accountId, SseEventFrame frame) {
    sendToRegisteredSinks(sinksByAccount, accountId, frame);
  }

  public void closeIncidentConnections(UUID incidentId) {
    var sinks = sinksByIncident.remove(incidentId);
    if (sinks == null) {
      return;
    }
    sinks.forEach(this::closeSink);
  }

  public List<SseLiveEventSink> sinks(UUID incidentId) {
    return List.copyOf(sinksByIncident.getOrDefault(incidentId, new CopyOnWriteArrayList<>()));
  }

  private AutoCloseable register(
      ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> registeredSinks,
      UUID subscriptionTargetId,
      SseLiveEventSink sink) {
    registeredSinks.compute(
        subscriptionTargetId,
        (ignored, sinks) -> {
          if (sinks == null) {
            sinks = new CopyOnWriteArrayList<>();
          }
          sinks.add(sink);
          return sinks;
        });
    return () -> unregister(registeredSinks, subscriptionTargetId, sink);
  }

  private void sendToRegisteredSinks(
      ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> registeredSinks,
      UUID subscriptionTargetId,
      SseEventFrame frame) {
    var sinks = registeredSinks.get(subscriptionTargetId);
    if (sinks == null) {
      return;
    }
    for (var sink : sinks) {
      try {
        sink.send(frame);
      } catch (IllegalStateException | UncheckedIOException exception) {
        // Spring emitter의 내부 처리 실패는 원인 예외를 감싼다. 종료된 연결과 구분한다.
        if (exception instanceof IllegalStateException && exception.getCause() != null) {
          throw exception;
        }
        unregister(registeredSinks, subscriptionTargetId, sink);
        log.warn(
            "SSE connection removed after send failure. eventId={}, sequence={}, failureType={}",
            frame.data().eventId(),
            frame.id(),
            exception.getClass().getSimpleName());
        // I/O 오류의 HTTP 연결 정리는 Servlet 컨테이너가 담당한다.
        if (!(exception instanceof UncheckedIOException)) {
          closeSink(sink);
        }
      }
    }
  }

  private void closeSink(SseLiveEventSink sink) {
    try {
      sink.close();
    } catch (RuntimeException exception) {
      log.warn("SSE connection close failed. failureType={}", exception.getClass().getSimpleName());
    }
  }

  private void unregister(
      ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> registeredSinks,
      UUID subscriptionTargetId,
      SseLiveEventSink sink) {
    registeredSinks.computeIfPresent(
        subscriptionTargetId,
        (ignored, sinks) -> {
          sinks.remove(sink);
          return sinks.isEmpty() ? null : sinks;
        });
  }
}
