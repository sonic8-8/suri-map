package com.surimap.board;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record BoardRefetchSignal(
    String eventId,
    String incidentId,
    String eventType,
    OffsetDateTime serverTs,
    long sseSequence,
    String slot,
    String sourceSpec,
    String entityId,
    String status,
    long version,
    long sequence,
    String sourceHash,
    Map<String, Object> payload) {

  public BoardRefetchSignal {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(serverTs, "serverTs must not be null");
    Objects.requireNonNull(slot, "slot must not be null");
    Objects.requireNonNull(sourceSpec, "sourceSpec must not be null");
    Objects.requireNonNull(entityId, "entityId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(sourceHash, "sourceHash must not be null");
    payload =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(payload, "payload must not be null")));
  }

  static BoardRefetchSignal fromRow(
      String incidentId, String eventType, OffsetDateTime serverTs, BoardSourceRow row) {
    return new BoardRefetchSignal(
        row.latestEventId(),
        incidentId,
        eventType,
        serverTs,
        row.sequence(),
        row.slot(),
        row.sourceSpec(),
        row.sourceResponseId(),
        row.status(),
        row.version(),
        row.sequence(),
        row.sourceHash(),
        row.payload());
  }
}
