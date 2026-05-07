package com.surimap.maparea.assignment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SEARCH_AREA_ASSIGNMENT_CHANGED 이벤트 payload record (S2.json §events_published).
 *
 * <p>EventHub.publish PublishRequest 생성 시 payload로 사용한다.
 */
public record SearchAreaAssignmentChangedEvent(
    String type,
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID searchAreaId,
    List<UUID> assignedAccountIds,
    String status,
    long version,
    Instant serverTs) {

  public SearchAreaAssignmentChangedEvent {
    if (type == null || !type.equals("SEARCH_AREA_ASSIGNMENT_CHANGED")) {
      throw new IllegalArgumentException("type must be SEARCH_AREA_ASSIGNMENT_CHANGED");
    }
  }
}
