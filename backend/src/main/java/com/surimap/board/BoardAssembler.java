package com.surimap.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BoardAssembler {

  public BoardDTO assemble(BoardAssemblyRequest request) {
    Objects.requireNonNull(request, "request must not be null");

    Map<String, List<BoardSlotRow>> slots = emptySlotMap();
    Map<String, List<BoardSourceRowCursor>> cursors = emptyCursorMap();
    long boardResponseVersion = request.boardResponseVersion();
    boolean terminalBoard = hasTerminalIncidentRow(request.sourceRows());

    for (BoardSourceRow row : request.sourceRows()) {
      BoardSlotRegistry.requireKnown(row.slot());
      boardResponseVersion = Math.max(boardResponseVersion, row.version());
      if (isHiddenClosedFreshnessRow(terminalBoard, row)) {
        continue;
      }

      BoardSlotRow slotRow = BoardSlotRow.from(row);
      BoardSourceRowCursor cursor = BoardSourceRowCursor.from(row);
      slots.get(row.slot()).add(slotRow);
      cursors.get(row.slot()).add(cursor);
    }

    Map<String, List<BoardSlotRow>> immutableSlotRows = freeze(slots);
    Map<String, Object> responseSlots = responseSlots(immutableSlotRows);
    Map<String, List<BoardSourceRowCursor>> immutableCursors = freeze(cursors);
    return new BoardDTO(
        request.incidentId(),
        request.boardResponseId(),
        boardResponseVersion,
        request.serverTs(),
        request.activeOpId(),
        request.selectedOpIds(),
        request.geometryHash(),
        responseSlots,
        immutableSlotRows,
        immutableCursors,
        immutableCursors,
        immutableCursors);
  }

  private static Map<String, List<BoardSlotRow>> emptySlotMap() {
    Map<String, List<BoardSlotRow>> slots = new LinkedHashMap<>();
    for (String slot : BoardSlotRegistry.requiredSlots()) {
      slots.put(slot, new ArrayList<>());
    }
    return slots;
  }

  private static Map<String, List<BoardSourceRowCursor>> emptyCursorMap() {
    Map<String, List<BoardSourceRowCursor>> cursors = new LinkedHashMap<>();
    for (String slot : BoardSlotRegistry.requiredSlots()) {
      cursors.put(slot, new ArrayList<>());
    }
    return cursors;
  }

  private static <T> Map<String, List<T>> freeze(Map<String, List<T>> slots) {
    Map<String, List<T>> frozen = new LinkedHashMap<>();
    for (Map.Entry<String, List<T>> entry : slots.entrySet()) {
      frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Collections.unmodifiableMap(frozen);
  }

  private static boolean hasTerminalIncidentRow(List<BoardSourceRow> rows) {
    return rows.stream().anyMatch(BoardSlotRow::isTerminalIncidentRow);
  }

  private static boolean isHiddenClosedFreshnessRow(boolean terminalBoard, BoardSourceRow row) {
    return terminalBoard && row.slot().equals("police_phone_freshness");
  }

  private static Map<String, Object> responseSlots(Map<String, List<BoardSlotRow>> slotRows) {
    Map<String, Object> responseSlots = new LinkedHashMap<>();
    for (Map.Entry<String, List<BoardSlotRow>> entry : slotRows.entrySet()) {
      String slot = entry.getKey();
      List<BoardSlotRow> rows = entry.getValue();
      if (BoardSlotRegistry.isSingletonSlot(slot)) {
        responseSlots.put(slot, singletonSlot(slot, rows));
      } else {
        responseSlots.put(slot, rows.stream().map(BoardSlotRow::responseFields).toList());
      }
    }
    return Collections.unmodifiableMap(responseSlots);
  }

  private static Map<String, Object> singletonSlot(String slot, List<BoardSlotRow> rows) {
    if (rows.isEmpty()) {
      return Map.of();
    }
    if (rows.size() > 1) {
      throw new IllegalArgumentException("singleton board slot has multiple rows: " + slot);
    }
    return rows.get(0).responseFields();
  }
}
