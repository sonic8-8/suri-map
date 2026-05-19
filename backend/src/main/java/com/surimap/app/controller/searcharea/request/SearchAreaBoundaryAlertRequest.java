package com.surimap.app.controller.searcharea.request;

import com.surimap.app.service.searcharea.SearchAreaBoundaryAlertException;
import com.surimap.app.service.searcharea.request.SearchAreaBoundaryAlertServiceRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchAreaBoundaryAlertRequest(
    UUID incidentId,
    UUID opId,
    UUID searchAreaId,
    String alertType,
    GeoJsonPointRequest location,
    Instant clientTs,
    UUID pathId,
    Integer clockOffsetMs) {

  public SearchAreaBoundaryAlertServiceRequest toServiceRequest(
      UUID policePhoneId, String idempotencyKey) {
    if (location == null) {
      throw new SearchAreaBoundaryAlertException("invalid_geometry");
    }
    return new SearchAreaBoundaryAlertServiceRequest(
        incidentId,
        opId,
        searchAreaId,
        alertType,
        location.lon(),
        location.lat(),
        clientTs,
        pathId,
        clockOffsetMs,
        policePhoneId,
        idempotencyKey);
  }

  public record GeoJsonPointRequest(String type, List<BigDecimal> coordinates) {
    BigDecimal lon() {
      if (!"Point".equals(type) || coordinates == null || coordinates.size() != 2) {
        throw new SearchAreaBoundaryAlertException("invalid_geometry");
      }
      return coordinates.get(0);
    }

    BigDecimal lat() {
      if (!"Point".equals(type) || coordinates == null || coordinates.size() != 2) {
        throw new SearchAreaBoundaryAlertException("invalid_geometry");
      }
      return coordinates.get(1);
    }
  }
}
