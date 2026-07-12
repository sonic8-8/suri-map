package com.surimap.api.controller.path.response;

import com.surimap.api.service.path.response.SearchPathSegmentCorrectionServiceResponse;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathSegmentCorrectionResponse {

  private String id;
  private MovementType movementType;
  private MovementTypeSource movementTypeSource;
  private UUID opId;
  private UUID correctedByAccountId;
  private OffsetDateTime correctedAt;
  private long version;

  @Builder
  private SearchPathSegmentCorrectionResponse(
      String id,
      MovementType movementType,
      MovementTypeSource movementTypeSource,
      UUID opId,
      UUID correctedByAccountId,
      OffsetDateTime correctedAt,
      long version) {
    this.id = id;
    this.movementType = movementType;
    this.movementTypeSource = movementTypeSource;
    this.opId = opId;
    this.correctedByAccountId = correctedByAccountId;
    this.correctedAt = correctedAt;
    this.version = version;
  }

  public static SearchPathSegmentCorrectionResponse from(
      SearchPathSegmentCorrectionServiceResponse response) {
    return SearchPathSegmentCorrectionResponse.builder()
        .id(response.getId())
        .movementType(response.getMovementType())
        .movementTypeSource(response.getMovementTypeSource())
        .opId(response.getOpId())
        .correctedByAccountId(response.getCorrectedByAccountId())
        .correctedAt(response.getCorrectedAt())
        .version(response.getVersion())
        .build();
  }
}
