package com.surimap.api.controller.path.response;

import com.surimap.api.service.path.response.SearchPathSegmentServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathSegmentResponse {

  private String id;
  private long version;
  private MovementType movementType;
  private MovementTypeSource movementTypeSource;
  private int startIndex;
  private int endIndex;
  private String startPointId;
  private String endPointId;
  private UUID correctedByAccountId;
  private OffsetDateTime correctedAt;

  @Builder
  private SearchPathSegmentResponse(
      String id,
      long version,
      MovementType movementType,
      MovementTypeSource movementTypeSource,
      int startIndex,
      int endIndex,
      String startPointId,
      String endPointId,
      UUID correctedByAccountId,
      OffsetDateTime correctedAt) {
    this.id = id;
    this.version = version;
    this.movementType = movementType;
    this.movementTypeSource = movementTypeSource;
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.startPointId = startPointId;
    this.endPointId = endPointId;
    this.correctedByAccountId = correctedByAccountId;
    this.correctedAt = correctedAt;
  }

  public static SearchPathSegmentResponse from(SearchPathSegmentServiceResponse segment) {
    return SearchPathSegmentResponse.builder()
        .id(segment.getId())
        .version(segment.getVersion())
        .movementType(segment.getMovementType())
        .movementTypeSource(segment.getMovementTypeSource())
        .startIndex(segment.getStartIndex())
        .endIndex(segment.getEndIndex())
        .startPointId(segment.getStartPointId())
        .endPointId(segment.getEndPointId())
        .correctedByAccountId(segment.getCorrectedByAccountId())
        .correctedAt(segment.getCorrectedAt())
        .build();
  }
}
