package com.surimap.board;

import java.util.Objects;

public record BoardRefetchConvergenceProbe(
    String eventId,
    String slot,
    String sourceSpec,
    String entityId,
    long sourceResponseVersion,
    long sourceResponseSequence,
    long boardRowVersion,
    long boardRowSequence,
    boolean converged) {

  public BoardRefetchConvergenceProbe {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(slot, "slot must not be null");
    Objects.requireNonNull(sourceSpec, "sourceSpec must not be null");
    Objects.requireNonNull(entityId, "entityId must not be null");
  }
}
