package com.surimap.api.service.path.response;

import com.surimap.domain.path.SearchPathStatus;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathPointsAppendServiceResponse {

  private UUID id;
  private UUID dutyShiftId;
  private UUID opId;
  private UUID accountId;
  private int acceptedPointCount;
  private int excludedPointCount;
  private List<SearchPathExcludedPointServiceResponse> excludedPoints;
  private List<List<Double>> geometry;
  private List<SearchPathSegmentServiceResponse> segments;
  private long version;
  private SearchPathStatus status;

  @Builder
  private SearchPathPointsAppendServiceResponse(
      UUID id,
      UUID dutyShiftId,
      UUID opId,
      UUID accountId,
      int acceptedPointCount,
      int excludedPointCount,
      List<SearchPathExcludedPointServiceResponse> excludedPoints,
      List<List<Double>> geometry,
      List<SearchPathSegmentServiceResponse> segments,
      long version,
      SearchPathStatus status) {
    this.id = id;
    this.dutyShiftId = dutyShiftId;
    this.opId = opId;
    this.accountId = accountId;
    this.acceptedPointCount = acceptedPointCount;
    this.excludedPointCount = excludedPointCount;
    this.excludedPoints = excludedPoints;
    this.geometry = geometry;
    this.segments = segments;
    this.version = version;
    this.status = status;
  }
}
