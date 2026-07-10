package com.surimap.api.controller.path.response;

import com.surimap.api.service.path.response.SearchPathQuerySegmentServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathQuerySegmentResponse {

  private String id;
  private long version;
  private MovementType movementType;
  private MovementTypeSource movementTypeSource;

  private LineStringGeometryJson geometry;

  private OffsetDateTime startedAt;
  private OffsetDateTime endedAt;
  private UUID correctedByAccountId;
  private OffsetDateTime correctedAt;

  @Builder
  private SearchPathQuerySegmentResponse(
      String id,
      long version,
      MovementType movementType,
      MovementTypeSource movementTypeSource,
      LineStringGeometryJson geometry,
      OffsetDateTime startedAt,
      OffsetDateTime endedAt,
      UUID correctedByAccountId,
      OffsetDateTime correctedAt) {
    this.id = id;
    this.version = version;
    this.movementType = movementType;
    this.movementTypeSource = movementTypeSource;
    this.geometry = geometry;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.correctedByAccountId = correctedByAccountId;
    this.correctedAt = correctedAt;
  }

  public static SearchPathQuerySegmentResponse from(
      SearchPathQuerySegmentServiceResponse response) {
    return SearchPathQuerySegmentResponse.builder()
        .id(response.getId())
        .version(response.getVersion())
        .movementType(response.getMovementType())
        .movementTypeSource(response.getMovementTypeSource())
        .geometry(LineStringGeometryJson.from(response.getGeometry()))
        .startedAt(response.getStartedAt())
        .endedAt(response.getEndedAt())
        .correctedByAccountId(response.getCorrectedByAccountId())
        .correctedAt(response.getCorrectedAt())
        .build();
  }
}
