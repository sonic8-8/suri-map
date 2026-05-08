package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.validation.BaseEventValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemorySseReplayEventStore implements SseReplayEventStore {

  private final Clock clock;
  private final ConcurrentMap<UUID, SseReplayEvent> byEventId = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, ConcurrentSkipListMap<Long, SseReplayEvent>> byIncident =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, AtomicLong> sequenceByIncident = new ConcurrentHashMap<>();

  public InMemorySseReplayEventStore() {
    this(Clock.systemUTC());
  }

  public InMemorySseReplayEventStore(Clock clock) {
    this.clock = clock;
  }

  @Override
  public SseReplayEvent save(SseReplayEvent event) {
    byEventId.put(event.envelope().eventId(), event);
    byIncident
        .computeIfAbsent(event.incidentId(), ignored -> new ConcurrentSkipListMap<>())
        .put(event.replaySequence(), event);
    sequenceByIncident
        .computeIfAbsent(event.incidentId(), ignored -> new AtomicLong())
        .updateAndGet(current -> Math.max(current, event.replaySequence()));
    return event;
  }

  @Override
  public ReplayAppend append(UUID eventDispatchJobId, PublishRequest envelope) {
    BaseEventValidator.validate(envelope);
    var existing = byEventId.get(envelope.eventId());
    if (existing != null) {
      return toAppend(existing);
    }

    long replaySequence = nextReplaySequence(envelope.incidentId(), eventDispatchJobId);
    var event =
        SseReplayEvent.active(
            UUID.randomUUID(),
            eventDispatchJobId,
            envelope.incidentId(),
            replaySequence,
            envelope,
            now());

    var raced = byEventId.putIfAbsent(envelope.eventId(), event);
    if (raced != null) {
      return toAppend(raced);
    }
    byIncident
        .computeIfAbsent(envelope.incidentId(), ignored -> new ConcurrentSkipListMap<>())
        .put(replaySequence, event);
    return toAppend(event);
  }

  @Override
  public Optional<ReplayAppend> findByEventId(UUID eventId) {
    return Optional.ofNullable(byEventId.get(eventId)).map(this::toAppend);
  }

  @Override
  public List<SseReplayEvent> findByIncidentId(UUID incidentId) {
    return byIncident.getOrDefault(incidentId, new ConcurrentSkipListMap<>()).values().stream()
        .sorted(Comparator.comparingLong(SseReplayEvent::replaySequence))
        .toList();
  }

  @Override
  public List<SseReplayEvent> replayAfter(UUID incidentId, long replaySequence) {
    return byIncident
        .getOrDefault(incidentId, new ConcurrentSkipListMap<>())
        .tailMap(replaySequence, false)
        .values()
        .stream()
        .filter(event -> SseReplayEvent.ACTIVE.equals(event.replayStatus()))
        .toList();
  }

  @Override
  public void clear() {
    byEventId.clear();
    byIncident.clear();
    sequenceByIncident.clear();
  }

  private long nextReplaySequence(UUID incidentId, UUID eventDispatchJobId) {
    var sequence = sequenceByIncident.computeIfAbsent(incidentId, ignored -> new AtomicLong());
    return sequence.incrementAndGet();
  }

  private Instant now() {
    return Instant.now(clock);
  }

  private ReplayAppend toAppend(SseReplayEvent event) {
    return new ReplayAppend(
        event.envelope().eventId(), event.incidentId(), event.replaySequence(), event);
  }
}
