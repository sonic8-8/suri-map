package com.surimap.board;

import java.util.Objects;

public record BoardRefetchLedgerEntry(
    String eventId,
    String incidentId,
    String slot,
    String sourceSpec,
    String entityId,
    String status,
    long version,
    long sequence,
    String sourceHash,
    BoardRefetchLedgerStatus applyStatus,
    long boardResponseVersion,
    BoardReloadReason reloadReason,
    long previousVersion,
    long previousSequence) {

  public BoardRefetchLedgerEntry {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(slot, "slot must not be null");
    Objects.requireNonNull(sourceSpec, "sourceSpec must not be null");
    Objects.requireNonNull(entityId, "entityId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(sourceHash, "sourceHash must not be null");
    Objects.requireNonNull(applyStatus, "applyStatus must not be null");
    Objects.requireNonNull(reloadReason, "reloadReason must not be null");
  }

  static BoardRefetchLedgerEntry forRow(
      BoardRefetchSignal incoming,
      BoardRefetchLedgerStatus status,
      BoardReloadReason reloadReason,
      BoardSourceRow previous,
      long boardResponseVersion) {
    return new BoardRefetchLedgerEntry(
        incoming.eventId(),
        incoming.incidentId(),
        incoming.slot(),
        incoming.sourceSpec(),
        incoming.entityId(),
        incoming.status(),
        incoming.version(),
        incoming.sequence(),
        incoming.sourceHash(),
        status,
        boardResponseVersion,
        reloadReason,
        previous == null ? -1 : previous.version(),
        previous == null ? -1 : previous.sequence());
  }

  static BoardRefetchLedgerEntry goneRefetchRequired(
      String incidentId, String lastEventId, long boardResponseVersion) {
    return new BoardRefetchLedgerEntry(
        lastEventId,
        incidentId,
        "board",
        "S4",
        incidentId,
        "REFETCH_REQUIRED",
        -1,
        -1,
        "",
        BoardRefetchLedgerStatus.REFETCH_REQUIRED,
        boardResponseVersion,
        BoardReloadReason.GONE_REFETCH_REQUIRED,
        -1,
        -1);
  }
}
