package com.surimap.board;

import java.util.List;
import java.util.Set;

public final class BoardSlotRegistry {

  private static final List<String> REQUIRED_SLOTS =
      List.of(
          "overall_search_area",
          "area",
          "path",
          "police_phone_freshness",
          "marker",
          "toast",
          "package_badge",
          "op_toggle",
          "op_history",
          "handover_memo",
          "handover_status",
          "search_history_summary",
          "incident_terminal");

  private static final Set<String> REQUIRED_SLOT_SET = Set.copyOf(REQUIRED_SLOTS);
  private static final Set<String> SINGLETON_SLOT_SET =
      Set.of("overall_search_area", "handover_status", "incident_terminal");

  private BoardSlotRegistry() {}

  public static List<String> requiredSlots() {
    return REQUIRED_SLOTS;
  }

  public static boolean isKnown(String slot) {
    return REQUIRED_SLOT_SET.contains(slot);
  }

  public static boolean isSingletonSlot(String slot) {
    requireKnown(slot);
    return SINGLETON_SLOT_SET.contains(slot);
  }

  public static void requireKnown(String slot) {
    if (!isKnown(slot)) {
      throw new IllegalArgumentException("unknown board slot: " + slot);
    }
  }
}
