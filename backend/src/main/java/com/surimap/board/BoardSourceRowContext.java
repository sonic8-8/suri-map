package com.surimap.board;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record BoardSourceRowContext(
    UUID incidentId, List<UUID> requestedOpIds, List<String> includeSlots, Long sinceVersion) {

  public BoardSourceRowContext {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    requestedOpIds =
        requestedOpIds == null
            ? List.of()
            : List.copyOf(new LinkedHashSet<>(requestedOpIds));
    includeSlots = normalizeSlots(includeSlots);
  }

  public boolean includes(String slot) {
    BoardSlotRegistry.requireKnown(slot);
    return includeSlots.isEmpty() || includeSlots.contains(slot);
  }

  private static List<String> normalizeSlots(List<String> slots) {
    if (slots == null || slots.isEmpty()) {
      return List.of();
    }
    Set<String> normalized = new LinkedHashSet<>();
    for (String slot : slots) {
      BoardSlotRegistry.requireKnown(slot);
      normalized.add(slot);
    }
    return List.copyOf(normalized);
  }
}
