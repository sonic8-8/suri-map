package com.surimap.board;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentBoardQueryService {

  private final BoardAssembler boardAssembler = new BoardAssembler();
  private final Clock clock;
  private final IncidentBoardSourceRowCollector sourceRowCollector;

  public IncidentBoardQueryService(Clock clock, IncidentBoardSourceRowCollector sourceRowCollector) {
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.sourceRowCollector =
        Objects.requireNonNull(sourceRowCollector, "sourceRowCollector must not be null");
  }

  public BoardDTO getBoard(
      UUID incidentId, List<UUID> opIds, List<String> includeSlots, Long sinceVersion) {
    IncidentBoardSourceRowSnapshot snapshot =
        sourceRowCollector.collect(new BoardSourceRowContext(incidentId, opIds, includeSlots, sinceVersion));
    BoardAssemblyRequest request =
        new BoardAssemblyRequest(
            incidentId.toString(),
            "bs-" + incidentId,
            0L,
            OffsetDateTime.now(clock),
            toString(snapshot.activeOpId()),
            snapshot.selectedOpIds().stream().map(UUID::toString).toList(),
            snapshot.geometryHash(),
            snapshot.sourceRows());
    return boardAssembler.assemble(request);
  }

  private static String toString(UUID value) {
    return value == null ? null : value.toString();
  }
}
