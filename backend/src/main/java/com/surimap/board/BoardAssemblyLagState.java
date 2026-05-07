package com.surimap.board;

import java.util.Objects;

public record BoardAssemblyLagState(
    String uiState,
    String eventId,
    String slot,
    String sourceSpec,
    String entityId,
    long sourceResponseVersion,
    long sourceResponseSequence,
    long staleResponseVersion,
    long staleResponseSequence,
    String reloadAssertion) {

  public static final String CURRENT = "CURRENT";
  public static final String STALE_REFETCH = "STALE_REFETCH";

  public BoardAssemblyLagState {
    Objects.requireNonNull(uiState, "uiState must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(slot, "slot must not be null");
    Objects.requireNonNull(sourceSpec, "sourceSpec must not be null");
    Objects.requireNonNull(entityId, "entityId must not be null");
    Objects.requireNonNull(reloadAssertion, "reloadAssertion must not be null");
  }
}
