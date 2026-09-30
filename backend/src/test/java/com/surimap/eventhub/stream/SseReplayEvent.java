package com.surimap.eventhub.stream;

import com.surimap.global.event.EventPublishRequest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SseReplayEvent(
    UUID id,
    UUID eventDispatchJobId,
    UUID incidentId,
    long replaySequence,
    EventPublishRequest envelope,
    String replayStatus,
    Instant createdAt,
    Instant purgedAt) {

  public static final String ACTIVE = "ACTIVE";

  public SseReplayEvent {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(eventDispatchJobId, "eventDispatchJobId must not be null");
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(envelope, "envelope must not be null");
    Objects.requireNonNull(replayStatus, "replayStatus must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    if (replaySequence <= 0) {
      throw new IllegalArgumentException("replaySequence must be positive");
    }
  }

  public static SseReplayEvent active(
      UUID id,
      UUID eventDispatchJobId,
      UUID incidentId,
      long replaySequence,
      EventPublishRequest envelope,
      Instant createdAt) {
    return new SseReplayEvent(
        id, eventDispatchJobId, incidentId, replaySequence, envelope, ACTIVE, createdAt, null);
  }
}
