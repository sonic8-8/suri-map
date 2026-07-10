package com.surimap.domain.path;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathExcludedPoint {

  private UUID id;
  private UUID searchPathId;
  private String pointId;
  private String reason;
  private OffsetDateTime clientTs;
  private Instant createdAt;
  private Instant updatedAt;

  @Builder(toBuilder = true)
  private SearchPathExcludedPoint(
      UUID id,
      UUID searchPathId,
      String pointId,
      String reason,
      OffsetDateTime clientTs,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.searchPathId = searchPathId;
    this.pointId = pointId;
    this.reason = reason;
    this.clientTs = clientTs;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }
}
