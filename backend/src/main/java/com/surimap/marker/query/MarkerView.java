package com.surimap.marker.query;

import com.surimap.domain.marker.MarkerSource;
import com.surimap.domain.marker.MarkerStatus;
import com.surimap.domain.marker.MarkerSupportRequestType;
import com.surimap.domain.marker.MarkerType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

/** S5 marker read shape consumed by board/package callers. */
public record MarkerView(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID dutyShiftId,
    UUID accountId,
    UUID policePhoneId,
    MarkerType type,
    MarkerSupportRequestType supportRequestType,
    MarkerSource source,
    MarkerStatus status,
    long version,
    Point location,
    String memo,
    Instant occurredAt,
    List<MarkerPhotoSummary> photoSummary) {

  public MarkerView {
    photoSummary = List.copyOf(photoSummary);
  }
}
