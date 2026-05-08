package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SseReplayEventStore {

  SseReplayEvent save(SseReplayEvent event);

  ReplayAppend append(UUID eventDispatchJobId, PublishRequest envelope);

  Optional<ReplayAppend> findByEventId(UUID eventId);

  List<SseReplayEvent> findByIncidentId(UUID incidentId);

  List<SseReplayEvent> replayAfter(UUID incidentId, long replaySequence);

  void clear();

  record ReplayAppend(UUID eventId, UUID incidentId, long replaySequence, SseReplayEvent event) {}
}
