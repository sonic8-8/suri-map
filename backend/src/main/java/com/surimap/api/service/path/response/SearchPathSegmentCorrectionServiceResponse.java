package com.surimap.api.service.path.response;

import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import com.surimap.domain.path.SearchPathSegment;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathSegmentCorrectionServiceResponse {

  private String id;
  private MovementType movementType;
  private MovementTypeSource movementTypeSource;
  private UUID opId;
  private UUID correctedByAccountId;
  private OffsetDateTime correctedAt;
  private long version;

  @Builder
  private SearchPathSegmentCorrectionServiceResponse(
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

  public static SearchPathSegmentCorrectionServiceResponse from(
      SearchPathSegment segment, UUID opId) {
    return SearchPathSegmentCorrectionServiceResponse.builder()
        .id(segment.getId().toString())
        .movementType(segment.getMovementType())
        .movementTypeSource(segment.getMovementTypeSource())
        .opId(opId)
        .correctedByAccountId(segment.getCorrectedByAccountId())
        .correctedAt(segment.getCorrectedAt())
        .version(segment.getVersion())
        .build();
  }
}
