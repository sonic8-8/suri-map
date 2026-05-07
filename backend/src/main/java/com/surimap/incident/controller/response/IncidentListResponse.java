package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.ListResult;
import java.util.List;

/** GET /api/incidents의 items wrapper 응답. 각 item은 S1-1 목록 허용 필드만 담는다. */
public record IncidentListResponse(List<IncidentListItemResponse> items) {

  public IncidentListResponse {
    items = List.copyOf(items);
  }

  public static IncidentListResponse from(ListResult result) {
    return new IncidentListResponse(
        result.items().stream().map(IncidentListItemResponse::from).toList());
  }
}
