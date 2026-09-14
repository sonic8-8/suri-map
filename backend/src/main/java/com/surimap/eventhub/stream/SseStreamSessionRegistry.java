package com.surimap.eventhub.stream;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SseStreamSessionRegistry {

  private static final Logger log = LoggerFactory.getLogger(SseStreamSessionRegistry.class);

  private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> sinksByIncident =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> sinksByAccount =
      new ConcurrentHashMap<>();

  public AutoCloseable register(UUID incidentId, SseLiveEventSink sink) {
    sinksByIncident.computeIfAbsent(incidentId, ignored -> new CopyOnWriteArrayList<>()).add(sink);
    return () -> unregister(sinksByIncident, incidentId, sink);
  }

  public void send(UUID incidentId, SseEventFrame frame) {
    sendToRegisteredSinks(sinksByIncident, incidentId, frame);
  }

  public AutoCloseable registerAccount(UUID accountId, SseLiveEventSink sink) {
    sinksByAccount.computeIfAbsent(accountId, ignored -> new CopyOnWriteArrayList<>()).add(sink);
    return () -> unregister(sinksByAccount, accountId, sink);
  }

  public void sendToAccount(UUID accountId, SseEventFrame frame) {
    sendToRegisteredSinks(sinksByAccount, accountId, frame);
  }

  public void release(UUID incidentId) {
    var sinks = sinksByIncident.remove(incidentId);
    if (sinks == null) {
      return;
    }
    sinks.forEach(this::closeSink);
  }

  public List<SseLiveEventSink> sinks(UUID incidentId) {
    return List.copyOf(sinksByIncident.getOrDefault(incidentId, new CopyOnWriteArrayList<>()));
  }

  private void sendToRegisteredSinks(
      ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> registeredSinks,
      UUID subscriptionId,
      SseEventFrame frame) {
    var sinks = registeredSinks.get(subscriptionId);
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
        unregister(registeredSinks, subscriptionId, sink);
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
      UUID subscriptionId,
      SseLiveEventSink sink) {
    var sinks = registeredSinks.get(subscriptionId);
    if (sinks == null) {
      return;
    }
    sinks.remove(sink);
    if (sinks.isEmpty()) {
      registeredSinks.remove(subscriptionId, sinks);
    }
  }
}
