package com.surimap.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public final class BoardRefetchGuard {

  private final BoardAssembler assembler;

  public BoardRefetchGuard() {
    this(new BoardAssembler());
  }

  BoardRefetchGuard(BoardAssembler assembler) {
    this.assembler = Objects.requireNonNull(assembler, "assembler must not be null");
  }

  public BoardRefetchResult apply(
      BoardAssemblyRequest currentRequest, List<BoardRefetchSignal> refetchSignals) {
    Objects.requireNonNull(currentRequest, "currentRequest must not be null");
    Objects.requireNonNull(refetchSignals, "refetchSignals must not be null");

    Map<BoardRowKey, BoardSourceRow> rowsByKey = currentRowsByKey(currentRequest);
    Set<BoardEventApplicationKey> appliedEvents = currentEventApplications(currentRequest);
    List<BoardRefetchLedgerEntry> ledger = new ArrayList<>();
    long boardResponseVersion = currentRequest.boardResponseVersion();
    BoardRefetchSignal convergenceSignal = null;

    for (BoardRefetchSignal signal : refetchSignals) {
      BoardSlotRegistry.requireKnown(signal.slot());
      BoardRowKey rowKey = BoardRowKey.from(signal);
      BoardEventApplicationKey eventKey = BoardEventApplicationKey.from(signal);
      BoardSourceRow previous = rowsByKey.get(rowKey);
      BoardRefetchLedgerStatus applyStatus =
          evaluateStatus(previous, appliedEvents, eventKey, signal);
      BoardReloadReason reloadReason =
          applyStatus == BoardRefetchLedgerStatus.REFETCH_REQUIRED
              ? BoardReloadReason.MISSING_PREDECESSOR
              : BoardReloadReason.NONE;
      if (applyStatus == BoardRefetchLedgerStatus.APPLIED) {
        BoardSourceRow incoming = toSourceRow(signal, previous);
        rowsByKey.put(rowKey, incoming);
        appliedEvents.add(eventKey);
        boardResponseVersion = Math.max(boardResponseVersion, signal.version());
        convergenceSignal = signal;
      }
      ledger.add(
          BoardRefetchLedgerEntry.forRow(
              signal, applyStatus, reloadReason, previous, boardResponseVersion));
    }

    BoardDTO board =
        assembler.assemble(copyRequest(currentRequest, rowsByKey.values().stream().toList()));
    return new BoardRefetchResult(board, ledger, convergenceProbe(board, convergenceSignal));
  }

  public BoardRefetchResult goneRefetchRequired(
      BoardAssemblyRequest currentRequest, String lastEventId) {
    Objects.requireNonNull(currentRequest, "currentRequest must not be null");
    Objects.requireNonNull(lastEventId, "lastEventId must not be null");

    return new BoardRefetchResult(
        assembler.assemble(currentRequest),
        List.of(
            BoardRefetchLedgerEntry.goneRefetchRequired(
                currentRequest.incidentId(), lastEventId, currentRequest.boardResponseVersion())));
  }

  public BoardAssemblyLagState observeAssemblyLag(
      BoardAssemblyRequest currentRequest, BoardRefetchSignal signal) {
    Objects.requireNonNull(currentRequest, "currentRequest must not be null");
    Objects.requireNonNull(signal, "signal must not be null");
    BoardSlotRegistry.requireKnown(signal.slot());

    BoardSourceRow staleRow = currentRowsByKey(currentRequest).get(BoardRowKey.from(signal));
    long staleVersion = staleRow == null ? -1 : staleRow.version();
    long staleSequence = staleRow == null ? -1 : staleRow.sequence();
    boolean lags = staleVersion < signal.version() || staleSequence < signal.sequence();

    return new BoardAssemblyLagState(
        lags ? BoardAssemblyLagState.STALE_REFETCH : BoardAssemblyLagState.CURRENT,
        signal.eventId(),
        signal.slot(),
        signal.sourceSpec(),
        signal.entityId(),
        signal.version(),
        signal.sequence(),
        staleVersion,
        staleSequence,
        reloadAssertion(signal));
  }

  private static Map<BoardRowKey, BoardSourceRow> currentRowsByKey(
      BoardAssemblyRequest currentRequest) {
    Map<BoardRowKey, BoardSourceRow> rowsByKey = new LinkedHashMap<>();
    for (BoardSourceRow row : currentRequest.sourceRows()) {
      BoardSlotRegistry.requireKnown(row.slot());
      rowsByKey.put(BoardRowKey.from(row), row);
    }
    return rowsByKey;
  }

  private static Set<BoardEventApplicationKey> currentEventApplications(
      BoardAssemblyRequest currentRequest) {
    Set<BoardEventApplicationKey> eventIds = new LinkedHashSet<>();
    for (BoardSourceRow row : currentRequest.sourceRows()) {
      eventIds.add(BoardEventApplicationKey.from(row));
    }
    return eventIds;
  }

  private static BoardRefetchLedgerStatus evaluateStatus(
      BoardSourceRow previous,
      Set<BoardEventApplicationKey> appliedEvents,
      BoardEventApplicationKey eventKey,
      BoardRefetchSignal incoming) {
    if (appliedEvents.contains(eventKey)) {
      return BoardRefetchLedgerStatus.DUPLICATE;
    }
    if (previous != null
        && (incoming.version() <= previous.version()
            || incoming.sequence() <= previous.sequence())) {
      return BoardRefetchLedgerStatus.STALE;
    }
    if (previous != null && incoming.sequence() > previous.sequence() + 1) {
      return BoardRefetchLedgerStatus.REFETCH_REQUIRED;
    }
    return BoardRefetchLedgerStatus.APPLIED;
  }

  private static BoardSourceRow toSourceRow(BoardRefetchSignal signal, BoardSourceRow previous) {
    String boardRowId =
        previous == null
            ? "board-" + signal.slot() + "-" + signal.entityId()
            : previous.boardRowId();
    return new BoardSourceRow(
        signal.slot(),
        signal.sourceSpec(),
        signal.entityId(),
        boardRowId,
        signal.status(),
        signal.version(),
        signal.sequence(),
        signal.eventId(),
        signal.sourceHash(),
        signal.payload());
  }

  private static BoardRefetchConvergenceProbe convergenceProbe(
      BoardDTO board, BoardRefetchSignal signal) {
    if (signal == null) {
      return null;
    }
    BoardSlotRow boardRow = findBoardRow(board, signal);
    return new BoardRefetchConvergenceProbe(
        signal.eventId(),
        signal.slot(),
        signal.sourceSpec(),
        signal.entityId(),
        signal.version(),
        signal.sequence(),
        boardRow == null ? -1 : boardRow.version(),
        boardRow == null ? -1 : boardRow.sequence(),
        boardRow != null
            && boardRow.version() >= signal.version()
            && boardRow.sequence() >= signal.sequence());
  }

  private static BoardSlotRow findBoardRow(BoardDTO board, BoardRefetchSignal signal) {
    try {
      return board.slotRow(signal.slot(), signal.entityId());
    } catch (NoSuchElementException ignored) {
      return null;
    }
  }

  private static String reloadAssertion(BoardRefetchSignal signal) {
    return "BoardDTO row version >= " + signal.version() + " and sequence >= " + signal.sequence();
  }

  private static BoardAssemblyRequest copyRequest(
      BoardAssemblyRequest currentRequest, List<BoardSourceRow> sourceRows) {
    return new BoardAssemblyRequest(
        currentRequest.incidentId(),
        currentRequest.boardResponseId(),
        currentRequest.boardResponseVersion(),
        currentRequest.serverTs(),
        currentRequest.activeOpId(),
        currentRequest.selectedOpIds(),
        currentRequest.geometryHash(),
        sourceRows);
  }

  private record BoardRowKey(String slot, String sourceSpec, String id) {

    static BoardRowKey from(BoardSourceRow row) {
      return new BoardRowKey(row.slot(), row.sourceSpec(), row.sourceResponseId());
    }

    static BoardRowKey from(BoardRefetchSignal row) {
      return new BoardRowKey(row.slot(), row.sourceSpec(), row.entityId());
    }
  }

  private record BoardEventApplicationKey(
      String eventId, String slot, String sourceSpec, String entityId) {

    static BoardEventApplicationKey from(BoardSourceRow row) {
      return new BoardEventApplicationKey(
          row.latestEventId(), row.slot(), row.sourceSpec(), row.sourceResponseId());
    }

    static BoardEventApplicationKey from(BoardRefetchSignal row) {
      return new BoardEventApplicationKey(
          row.eventId(), row.slot(), row.sourceSpec(), row.entityId());
    }
  }
}
