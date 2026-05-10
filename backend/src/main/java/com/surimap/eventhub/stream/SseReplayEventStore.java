package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface SseReplayEventStore {

  SseReplayEvent save(SseReplayEvent event);

  ReplayAppend append(UUID eventDispatchJobId, PublishRequest envelope);

  Optional<ReplayAppend> findByEventId(UUID eventId);

  List<SseReplayEvent> findByIncidentId(UUID incidentId);

  List<SseReplayEvent> replayAfter(UUID incidentId, long replaySequence);

  OptionalLong terminalReplaySequence(UUID incidentId);

  boolean isIncidentPurged(UUID incidentId);

  long purgeIncident(UUID incidentId, Instant purgedAt);

  default long purgeIncident(UUID incidentId) {
    return purgeIncident(incidentId, Instant.now());
  }

  void clear();

  record ReplayAppend(UUID eventId, UUID incidentId, long replaySequence, SseReplayEvent event, boolean isNew) {}
}
