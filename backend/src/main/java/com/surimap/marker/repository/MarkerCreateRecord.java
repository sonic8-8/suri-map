package com.surimap.marker.repository;

import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

public record MarkerCreateRecord(
    UUID incidentId,
    UUID id,
    UUID operationalPeriodId,
    UUID dutyShiftId,
    MarkerType markerType,
    MarkerSupportRequestType supportRequestType,
    Point location,
    String memo,
    Instant occurredAt,
    UUID createdByAccountId,
    UUID policePhoneId,
    MarkerSource markerSource,
    MarkerStatus status,
    long version) {

  public MarkerCreateRecord {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(operationalPeriodId, "operationalPeriodId must not be null");
    Objects.requireNonNull(markerType, "markerType must not be null");
    Objects.requireNonNull(location, "location must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(createdByAccountId, "createdByAccountId must not be null");
    Objects.requireNonNull(policePhoneId, "policePhoneId must not be null");
    Objects.requireNonNull(markerSource, "markerSource must not be null");
    Objects.requireNonNull(status, "status must not be null");
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive");
    }
  }
}
