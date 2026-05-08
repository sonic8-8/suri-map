package com.surimap.eventhub.stream;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SseStreamSessionRegistry {

  private final ConcurrentMap<UUID, CopyOnWriteArrayList<SseLiveEventSink>> sinksByIncident =
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
}
