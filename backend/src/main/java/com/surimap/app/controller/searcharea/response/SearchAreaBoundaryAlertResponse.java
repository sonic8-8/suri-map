package com.surimap.app.controller.searcharea.response;

import com.surimap.app.service.searcharea.SearchAreaBoundaryAlertResult;
import java.util.UUID;

public record SearchAreaBoundaryAlertResponse(
    UUID id,
    UUID eventId,
    UUID incidentId,
    UUID opId,
    UUID searchAreaId,
    UUID policePhoneId,
    String alertType,
    long version,
    String status) {

  public static SearchAreaBoundaryAlertResponse from(SearchAreaBoundaryAlertResult result) {
    return new SearchAreaBoundaryAlertResponse(
        result.id(),
        result.eventId(),
        result.incidentId(),
        result.opId(),
        result.searchAreaId(),
        result.policePhoneId(),
        result.alertType(),
        result.version(),
        result.status());
  }
}
