package com.surimap.api.controller.path.response;

import com.surimap.domain.path.PathExcludedPoint;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathExcludedPointResponse {

  private String pointId;
  private String reason;
  private OffsetDateTime clientTs;

  @Builder
  private SearchPathExcludedPointResponse(String pointId, String reason, OffsetDateTime clientTs) {
    this.pointId = pointId;
    this.reason = reason;
    this.clientTs = clientTs;
  }

  public static SearchPathExcludedPointResponse from(PathExcludedPoint point) {
    return SearchPathExcludedPointResponse.builder()
        .pointId(point.pointId())
        .reason(point.reason())
        .clientTs(point.clientTs())
        .build();
  }
}
