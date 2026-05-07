package com.surimap.board;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class BoardDTO {

  private final String incidentId;
  private final String boardResponseId;
  private final long boardResponseVersion;
  private final OffsetDateTime serverTs;
  private final String activeOpId;
  private final List<String> selectedOpIds;
  private final String geometryHash;
  private final Map<String, Object> slots;
  private final Map<String, List<BoardSlotRow>> slotRows;
  private final Map<String, List<BoardSourceRowCursor>> slotSources;
  private final Map<String, List<BoardSourceRowCursor>> sourceVersions;
  private final Map<String, List<BoardSourceRowCursor>> sourceHashes;

  BoardDTO(
      String incidentId,
      String boardResponseId,
      long boardResponseVersion,
      OffsetDateTime serverTs,
      String activeOpId,
      List<String> selectedOpIds,
      String geometryHash,
      Map<String, Object> slots,
      Map<String, List<BoardSlotRow>> slotRows,
      Map<String, List<BoardSourceRowCursor>> slotSources,
      Map<String, List<BoardSourceRowCursor>> sourceVersions,
      Map<String, List<BoardSourceRowCursor>> sourceHashes) {
    this.incidentId = Objects.requireNonNull(incidentId, "incidentId must not be null");
    this.boardResponseId =
        Objects.requireNonNull(boardResponseId, "boardResponseId must not be null");
    this.boardResponseVersion = boardResponseVersion;
    this.serverTs = Objects.requireNonNull(serverTs, "serverTs must not be null");
    this.activeOpId = activeOpId;
    this.selectedOpIds =
        List.copyOf(Objects.requireNonNull(selectedOpIds, "selectedOpIds must not be null"));
    this.geometryHash = Objects.requireNonNull(geometryHash, "geometryHash must not be null");
    this.slots = copyResponseSlotMap(slots);
    this.slotRows = copySlotRowMap(slotRows);
    this.slotSources = copyCursorMap(slotSources);
    this.sourceVersions = copyCursorMap(sourceVersions);
    this.sourceHashes = copyCursorMap(sourceHashes);
  }

  public String incidentId() {
    return incidentId;
  }

  public String boardResponseId() {
    return boardResponseId;
  }

  public long boardResponseVersion() {
    return boardResponseVersion;
  }

  public OffsetDateTime serverTs() {
    return serverTs;
  }

  public String activeOpId() {
    return activeOpId;
  }

  public List<String> selectedOpIds() {
    return selectedOpIds;
  }

  public String geometryHash() {
    return geometryHash;
  }

  public Map<String, Object> slots() {
    return slots;
  }

  public Map<String, List<BoardSourceRowCursor>> slotSources() {
    return slotSources;
  }

  public Map<String, List<BoardSourceRowCursor>> sourceVersions() {
    return sourceVersions;
  }

  public Map<String, List<BoardSourceRowCursor>> sourceHashes() {
    return sourceHashes;
  }

  public BoardSlotRow slotRow(String slot, String id) {
    return slotRows.getOrDefault(slot, List.of()).stream()
        .filter(row -> row.id().equals(id))
        .findFirst()
        .orElseThrow(
            () -> new NoSuchElementException("board slot row not found: " + slot + "/" + id));
  }

  private static Map<String, Object> copyResponseSlotMap(Map<String, Object> source) {
    Objects.requireNonNull(source, "source must not be null");
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof List<?> rows) {
        copy.put(entry.getKey(), List.copyOf(rows));
      } else if (value instanceof Map<?, ?> row) {
        copy.put(entry.getKey(), copyObjectMap(row));
      } else {
        copy.put(entry.getKey(), value);
      }
    }
    return Collections.unmodifiableMap(copy);
  }

  private static Map<String, Object> copyObjectMap(Map<?, ?> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      copy.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return Collections.unmodifiableMap(copy);
  }

  private static Map<String, List<BoardSlotRow>> copySlotRowMap(
      Map<String, List<BoardSlotRow>> source) {
    Objects.requireNonNull(source, "source must not be null");
    Map<String, List<BoardSlotRow>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<BoardSlotRow>> entry : source.entrySet()) {
      copy.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Collections.unmodifiableMap(copy);
  }

  private static Map<String, List<BoardSourceRowCursor>> copyCursorMap(
      Map<String, List<BoardSourceRowCursor>> source) {
    Objects.requireNonNull(source, "source must not be null");
    Map<String, List<BoardSourceRowCursor>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<BoardSourceRowCursor>> entry : source.entrySet()) {
      copy.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Collections.unmodifiableMap(copy);
  }
}
