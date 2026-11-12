package com.surimap.board;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record BoardAssemblyRequest(
    String incidentId,
    String boardResponseId,
    long boardResponseVersion,
    OffsetDateTime serverTs,
    String activeOpId,
    List<String> selectedOpIds,
    String geometryHash,
    List<BoardSourceRow> sourceRows,
    long lastSseSequence) {

  /** SSE 순번을 담지 않는 조립 요청이다. 순번은 0으로 둔다. */
  public BoardAssemblyRequest(
      String incidentId,
      String boardResponseId,
      long boardResponseVersion,
      OffsetDateTime serverTs,
      String activeOpId,
      List<String> selectedOpIds,
      String geometryHash,
      List<BoardSourceRow> sourceRows) {
    this(
        incidentId,
        boardResponseId,
        boardResponseVersion,
        serverTs,
        activeOpId,
        selectedOpIds,
        geometryHash,
        sourceRows,
        0L);
  }

  public BoardAssemblyRequest {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(boardResponseId, "boardResponseId must not be null");
    Objects.requireNonNull(serverTs, "serverTs must not be null");
    selectedOpIds =
        List.copyOf(Objects.requireNonNull(selectedOpIds, "selectedOpIds must not be null"));
    Objects.requireNonNull(geometryHash, "geometryHash must not be null");
    sourceRows = List.copyOf(Objects.requireNonNull(sourceRows, "sourceRows must not be null"));
  }
}
