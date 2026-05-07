package com.surimap.board;

import java.util.Objects;

public record BoardSourceRowCursor(
    String id,
    String status,
    long version,
    long sequence,
    String sourceSpec,
    String sourceHash,
    String latestEventId)
    implements BoardRowCursor {

  public BoardSourceRowCursor {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(sourceSpec, "sourceSpec must not be null");
    Objects.requireNonNull(sourceHash, "sourceHash must not be null");
    Objects.requireNonNull(latestEventId, "latestEventId must not be null");
  }

  static BoardSourceRowCursor from(BoardSourceRow row) {
    return new BoardSourceRowCursor(
        row.sourceResponseId(),
        row.status(),
        row.version(),
        row.sequence(),
        row.sourceSpec(),
        row.sourceHash(),
        row.latestEventId());
  }
}
