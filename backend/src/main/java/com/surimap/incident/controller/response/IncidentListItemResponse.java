package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.ListItem;
import java.time.Instant;
import java.util.UUID;

/** 사건 목록 item DTO. 상세 전용 missingPerson, assignments, terminal/purge 필드는 포함하지 않는다. */
public record IncidentListItemResponse(
    UUID id, UUID incidentId, String title, String status, long version, Instant closedAt) {

  public static IncidentListItemResponse from(ListItem item) {
    return new IncidentListItemResponse(
        item.id(), item.incidentId(), item.title(), item.status(), item.version(), item.closedAt());
  }
}
