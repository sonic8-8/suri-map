package com.surimap.api.service.path.response;

import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathQuerySegmentServiceResponse {

  private String id;
  private long version;
  private MovementType movementType;
  private MovementTypeSource movementTypeSource;
  private List<List<Double>> geometry;
  private OffsetDateTime startedAt;
  private OffsetDateTime endedAt;
  private UUID correctedByAccountId;
  private OffsetDateTime correctedAt;

  @Builder
  private SearchPathQuerySegmentServiceResponse(
      String id,
      long version,
      MovementType movementType,
      MovementTypeSource movementTypeSource,
      List<List<Double>> geometry,
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
}
