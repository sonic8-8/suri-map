package com.surimap.marker.seed;

import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

/** S1-1 mock 112 incident import에서 S5로 넘기는 초기 기준 마커 payload. */
public record SeedMarker(
    UUID id,
    UUID operationalPeriodId,
    UUID dutyShiftId,
    MarkerType markerType,
    MarkerSupportRequestType supportRequestType,
    Point location,
    String memo,
    Instant occurredAt,
    UUID createdByAccountId,
    UUID policePhoneId) {

  public SeedMarker {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(operationalPeriodId, "operationalPeriodId must not be null");
    Objects.requireNonNull(markerType, "markerType must not be null");
    Objects.requireNonNull(location, "location must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    Objects.requireNonNull(createdByAccountId, "createdByAccountId must not be null");
  }
}
