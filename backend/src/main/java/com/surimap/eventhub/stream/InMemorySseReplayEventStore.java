package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.validation.BaseEventValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
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
  private final ConcurrentMap<UUID, Instant> purgedIncidentAt = new ConcurrentHashMap<>();

  public InMemorySseReplayEventStore() {
    this(Clock.systemUTC());
  }

  public InMemorySseReplayEventStore(Clock clock) {
    this.clock = clock;
  }

  @Override
  public SseReplayEvent save(SseReplayEvent event) {
    if (isIncidentPurged(event.incidentId())) {
      throw new GoneRefetchRequiredException();
    }
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
    if (isIncidentPurged(envelope.incidentId())) {
      throw new GoneRefetchRequiredException();
    }

    // computeIfAbsent is atomic: the sequence is allocated only when this thread wins the slot,
    // eliminating the gap that would arise from pre-allocating a sequence before putIfAbsent.
    boolean[] created = {false};
    var event =
        byEventId.computeIfAbsent(
            envelope.eventId(),
            ignored -> {
              long seq = nextReplaySequence(envelope.incidentId(), eventDispatchJobId);
              created[0] = true;
              return SseReplayEvent.active(
                  UUID.randomUUID(),
                  eventDispatchJobId,
                  envelope.incidentId(),
                  seq,
                  envelope,
                  now());
            });

    if (created[0]) {
      byIncident
          .computeIfAbsent(envelope.incidentId(), ignored -> new ConcurrentSkipListMap<>())
          .put(event.replaySequence(), event);
    }
    return toAppend(event, created[0]);
  }

  @Override
  public Optional<ReplayAppend> findByEventId(UUID eventId) {
    return Optional.ofNullable(byEventId.get(eventId)).map(e -> toAppend(e, false));
  }

  @Override
  public List<SseReplayEvent> findByIncidentId(UUID incidentId) {
    return byIncident.getOrDefault(incidentId, new ConcurrentSkipListMap<>()).values().stream()
        .toList();
  }

  @Override
  public List<SseReplayEvent> replayAfter(UUID incidentId, long replaySequence) {
    if (isIncidentPurged(incidentId)) {
      throw new GoneRefetchRequiredException();
    }
    return byIncident
        .getOrDefault(incidentId, new ConcurrentSkipListMap<>())
        .tailMap(replaySequence, false)
        .values()
        .stream()
        .filter(event -> SseReplayEvent.ACTIVE.equals(event.replayStatus()))
        .toList();
  }

  @Override
  public OptionalLong terminalReplaySequence(UUID incidentId) {
    return byIncident
        .getOrDefault(incidentId, new ConcurrentSkipListMap<>())
        .values()
        .stream()
        .filter(event -> SseReplayEvent.ACTIVE.equals(event.replayStatus()))
        .filter(event -> "INCIDENT_CLOSED".equals(event.envelope().type()))
        .mapToLong(SseReplayEvent::replaySequence)
        .min();
  }

  @Override
  public boolean isIncidentPurged(UUID incidentId) {
    return purgedIncidentAt.containsKey(incidentId);
  }

  @Override
  public long purgeIncident(UUID incidentId, Instant purgedAt) {
    Objects.requireNonNull(purgedAt, "purgedAt must not be null");
    var removed = byIncident.remove(incidentId);
    long purgedCount = 0L;
    if (removed != null) {
      purgedCount = removed.size();
      removed.values().forEach(event -> byEventId.remove(event.envelope().eventId()));
    }
    sequenceByIncident.remove(incidentId);
    purgedIncidentAt.put(incidentId, purgedAt);
    return purgedCount;
  }

  @Override
  public void clear() {
    byEventId.clear();
    byIncident.clear();
    sequenceByIncident.clear();
    purgedIncidentAt.clear();
  }

  private long nextReplaySequence(UUID incidentId, UUID eventDispatchJobId) {
    var sequence = sequenceByIncident.computeIfAbsent(incidentId, ignored -> new AtomicLong());
    return sequence.incrementAndGet();
  }

  private Instant now() {
    return Instant.now(clock);
  }

  private ReplayAppend toAppend(SseReplayEvent event, boolean isNew) {
    return new ReplayAppend(
        event.envelope().eventId(), event.incidentId(), event.replaySequence(), event, isNew);
  }
}
