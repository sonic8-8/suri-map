package com.surimap.api.controller.path.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.surimap.api.service.path.response.SearchPathPointsAppendServiceResponse;
import com.surimap.domain.path.SearchPathStatus;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathPointsAppendResponse {

  private UUID id;
  private UUID dutyShiftId;
  private UUID opId;
  private UUID policePhoneId;
  private UUID accountId;
  private int acceptedPointCount;
  private int excludedPointCount;
  private List<SearchPathExcludedPointResponse> excludedPoints;

  @JsonSerialize(converter = LineStringGeometryJsonConverter.class)
  @JsonDeserialize(using = LineStringCoordinatesDeserializer.class)
  private List<List<Double>> geometry;

  private List<SearchPathSegmentResponse> segments;
  private long version;
  private SearchPathStatus status;

  @Builder
  private SearchPathPointsAppendResponse(
      UUID id,
      UUID dutyShiftId,
      UUID opId,
      UUID policePhoneId,
      UUID accountId,
      int acceptedPointCount,
      int excludedPointCount,
      List<SearchPathExcludedPointResponse> excludedPoints,
      List<List<Double>> geometry,
      List<SearchPathSegmentResponse> segments,
      long version,
      SearchPathStatus status) {
    this.id = id;
    this.dutyShiftId = dutyShiftId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
    this.acceptedPointCount = acceptedPointCount;
    this.excludedPointCount = excludedPointCount;
    this.excludedPoints = excludedPoints;
    this.geometry = geometry;
    this.segments = segments;
    this.version = version;
    this.status = status;
  }

  public static SearchPathPointsAppendResponse from(
      SearchPathPointsAppendServiceResponse response) {
    return SearchPathPointsAppendResponse.builder()
        .id(response.getId())
        .dutyShiftId(response.getDutyShiftId())
        .opId(response.getOpId())
        .policePhoneId(response.getPolicePhoneId())
        .accountId(response.getAccountId())
        .acceptedPointCount(response.getAcceptedPointCount())
        .excludedPointCount(response.getExcludedPointCount())
        .excludedPoints(
            response.getExcludedPoints().stream()
                .map(SearchPathExcludedPointResponse::from)
                .toList())
        .geometry(response.getGeometry())
        .segments(response.getSegments().stream().map(SearchPathSegmentResponse::from).toList())
        .version(response.getVersion())
        .status(response.getStatus())
        .build();
  }
}
