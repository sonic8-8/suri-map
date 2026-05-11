package com.surimap.board;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record IncidentBoardSourceRowSnapshot(
    UUID activeOpId, List<UUID> selectedOpIds, String geometryHash, List<BoardSourceRow> sourceRows) {

  public IncidentBoardSourceRowSnapshot {
    selectedOpIds =
        List.copyOf(Objects.requireNonNull(selectedOpIds, "selectedOpIds must not be null"));
    Objects.requireNonNull(geometryHash, "geometryHash must not be null");
    sourceRows = List.copyOf(Objects.requireNonNull(sourceRows, "sourceRows must not be null"));
  }
}
