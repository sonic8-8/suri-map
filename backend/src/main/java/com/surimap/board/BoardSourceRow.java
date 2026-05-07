package com.surimap.board;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record BoardSourceRow(
    String slot,
    String sourceSpec,
    String sourceResponseId,
    String boardRowId,
    String status,
    long version,
    long sequence,
    String latestEventId,
    String sourceHash,
    Map<String, Object> payload) {

  public BoardSourceRow {
    Objects.requireNonNull(slot, "slot must not be null");
    Objects.requireNonNull(sourceSpec, "sourceSpec must not be null");
    Objects.requireNonNull(sourceResponseId, "sourceResponseId must not be null");
    Objects.requireNonNull(boardRowId, "boardRowId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(latestEventId, "latestEventId must not be null");
    Objects.requireNonNull(sourceHash, "sourceHash must not be null");
    payload =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(payload, "payload must not be null")));
  }
}
