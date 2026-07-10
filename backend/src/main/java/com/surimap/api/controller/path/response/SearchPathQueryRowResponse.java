package com.surimap.api.controller.path.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.surimap.api.service.path.response.SearchPathQueryRowServiceResponse;
import com.surimap.domain.path.SearchPathStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathQueryRowResponse {

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

  @JsonSerialize(converter = LineStringGeometryJsonConverter.class)
  @JsonDeserialize(using = LineStringCoordinatesDeserializer.class)
  private List<List<Double>> geometry;

  private List<SearchPathQuerySegmentResponse> segments;
  private List<SearchPathExcludedPointResponse> excludedPoints;

  @Builder
  private SearchPathQueryRowResponse(
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
      List<SearchPathQuerySegmentResponse> segments,
      List<SearchPathExcludedPointResponse> excludedPoints) {
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

  public static SearchPathQueryRowResponse from(SearchPathQueryRowServiceResponse response) {
    return SearchPathQueryRowResponse.builder()
        .id(response.getId())
        .incidentId(response.getIncidentId())
        .opId(response.getOpId())
        .dutyShiftId(response.getDutyShiftId())
        .policePhoneId(response.getPolicePhoneId())
        .accountId(response.getAccountId())
        .status(response.getStatus())
        .startedAt(response.getStartedAt())
        .endedAt(response.getEndedAt())
        .version(response.getVersion())
        .geometry(response.getGeometry())
        .segments(
            response.getSegments().stream().map(SearchPathQuerySegmentResponse::from).toList())
        .excludedPoints(
            response.getExcludedPoints().stream()
                .map(SearchPathExcludedPointResponse::from)
                .toList())
        .build();
  }
}
