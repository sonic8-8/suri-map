package com.surimap.board;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentBoardQueryService {

  private static final String EMPTY_BOARD_GEOMETRY_HASH = "hash-board-geometry-current";

  private final BoardAssembler boardAssembler = new BoardAssembler();
  private final Clock clock;

  public IncidentBoardQueryService(Clock clock) {
    this.clock = clock;
  }

  public BoardDTO getBoard(
      UUID incidentId, List<UUID> opIds, List<String> includeSlots, Long sinceVersion) {
    List<String> selectedOpIds = selectedOpIds(opIds);
    BoardAssemblyRequest request =
        new BoardAssemblyRequest(
            incidentId.toString(),
            "bs-" + incidentId,
            0L,
            OffsetDateTime.now(clock),
            activeOpId(selectedOpIds),
            selectedOpIds,
            EMPTY_BOARD_GEOMETRY_HASH,
            List.of());
    return boardAssembler.assemble(request);
  }

  private static List<String> selectedOpIds(List<UUID> opIds) {
    if (opIds == null || opIds.isEmpty()) {
      return List.of();
    }
    return opIds.stream().map(UUID::toString).toList();
  }

  private static String activeOpId(List<String> selectedOpIds) {
    if (selectedOpIds.isEmpty()) {
      return null;
    }
    return selectedOpIds.get(0);
  }
}
