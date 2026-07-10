package com.surimap.api.service.path.response;

import com.surimap.domain.path.PathExcludedPoint;
import com.surimap.domain.path.SearchPathStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathQueryRowServiceResponse {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private UUID dutyShiftId;
  private UUID policePhoneId;
  private UUID accountId;
  private SearchPathStatus status;
  private Instant startedAt;
  private Instant endedAt;
  private long version;
  private List<List<Double>> geometry;
  private List<SearchPathQuerySegmentServiceResponse> segments;
  private List<PathExcludedPoint> excludedPoints;

  @Builder
  private SearchPathQueryRowServiceResponse(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID dutyShiftId,
      UUID policePhoneId,
      UUID accountId,
      SearchPathStatus status,
      Instant startedAt,
      Instant endedAt,
      long version,
      List<List<Double>> geometry,
      List<SearchPathQuerySegmentServiceResponse> segments,
      List<PathExcludedPoint> excludedPoints) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.dutyShiftId = dutyShiftId;
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
    this.status = status;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.version = version;
    this.geometry = geometry;
    this.segments = segments;
    this.excludedPoints = excludedPoints;
  }
}
