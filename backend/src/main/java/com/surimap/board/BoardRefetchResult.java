package com.surimap.board;

import java.util.List;
import java.util.Objects;

public record BoardRefetchResult(
    BoardDTO board,
    List<BoardRefetchLedgerEntry> ledger,
    BoardRefetchConvergenceProbe convergenceProbe) {

  public BoardRefetchResult(BoardDTO board, List<BoardRefetchLedgerEntry> ledger) {
    this(board, ledger, null);
  }

  public BoardRefetchResult {
    Objects.requireNonNull(board, "board must not be null");
    ledger = List.copyOf(Objects.requireNonNull(ledger, "ledger must not be null"));
  }
}
