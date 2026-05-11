package com.surimap.board;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record IncidentBoardResponse(
    String incidentId,
    long boardResponseVersion,
    OffsetDateTime serverTs,
    String activeOpId,
    List<String> selectedOpIds,
    Map<String, Object> slots,
    Map<String, List<BoardSourceRowCursor>> sourceVersions,
    String geometryHash,
    Map<String, List<BoardSourceRowCursor>> sourceHashes,
    Map<String, List<BoardSourceRowCursor>> slotSources) {

  static IncidentBoardResponse from(BoardDTO board) {
    return new IncidentBoardResponse(
        board.incidentId(),
        board.boardResponseVersion(),
        board.serverTs(),
        board.activeOpId(),
        board.selectedOpIds(),
        board.slots(),
        board.sourceVersions(),
        board.geometryHash(),
        board.sourceHashes(),
        board.slotSources());
  }
}
