package com.surimap.marker.notification.service;

import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import java.util.Objects;
import java.util.UUID;

public record MarkerNotificationContext(
    UUID markerId,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    MarkerType markerType,
    MarkerSupportRequestType supportRequestType,
    MarkerGeoJsonPoint location,
    long markerVersion) {

  public MarkerNotificationContext {
    Objects.requireNonNull(markerId, "markerId must not be null");
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(opId, "opId must not be null");
    Objects.requireNonNull(policePhoneId, "policePhoneId must not be null");
    Objects.requireNonNull(markerType, "markerType must not be null");
    Objects.requireNonNull(location, "location must not be null");
    if (markerVersion <= 0) {
      throw new IllegalArgumentException("markerVersion must be positive");
    }
  }
}
