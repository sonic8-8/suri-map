package com.surimap.eventhub.stream;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SseStreamSessionRegistry {

  private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> sinksByIncident =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> sinksByAccount =
      new ConcurrentHashMap<>();

  public AutoCloseable register(UUID incidentId, SseLiveEventSink sink) {
    sinksByIncident.computeIfAbsent(incidentId, ignored -> new CopyOnWriteArrayList<>()).add(sink);
    return () -> unregister(incidentId, sink);
  }

  public void send(UUID incidentId, SseEventFrame frame) {
    sinksByIncident
        .getOrDefault(incidentId, new CopyOnWriteArrayList<>())
        .forEach(sink -> sink.send(frame));
  }

  public AutoCloseable registerAccount(UUID accountId, SseLiveEventSink sink) {
    sinksByAccount.computeIfAbsent(accountId, ignored -> new CopyOnWriteArrayList<>()).add(sink);
    return () -> unregisterAccount(accountId, sink);
  }

  public void sendToAccount(UUID accountId, SseEventFrame frame) {
    sinksByAccount
        .getOrDefault(accountId, new CopyOnWriteArrayList<>())
        .forEach(sink -> sink.send(frame));
  }

  public void release(UUID incidentId) {
    var sinks = sinksByIncident.remove(incidentId);
    if (sinks == null) {
      return;
    }
    sinks.forEach(SseLiveEventSink::close);
  }

  public List<SseLiveEventSink> sinks(UUID incidentId) {
    return List.copyOf(sinksByIncident.getOrDefault(incidentId, new CopyOnWriteArrayList<>()));
  }

  private void unregister(UUID incidentId, SseLiveEventSink sink) {
    var sinks = sinksByIncident.get(incidentId);
    if (sinks == null) {
      return;
    }
    sinks.remove(sink);
    if (sinks.isEmpty()) {
      sinksByIncident.remove(incidentId, sinks);
    }
  }

  private void unregisterAccount(UUID accountId, SseLiveEventSink sink) {
    var sinks = sinksByAccount.get(accountId);
    if (sinks == null) {
      return;
    }
    sinks.remove(sink);
    if (sinks.isEmpty()) {
      sinksByAccount.remove(accountId, sinks);
    }
  }
}
