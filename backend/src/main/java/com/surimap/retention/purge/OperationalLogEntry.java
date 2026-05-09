package com.surimap.retention.purge;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OperationalLogEntry(
    String eventType,
    UUID incidentId,
    UUID purgeRunId,
    Instant occurredAt,
    Map<String, Object> metadata) {

  public OperationalLogEntry {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
