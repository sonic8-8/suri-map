package com.surimap.api.service.path.response;

import com.surimap.domain.path.SearchPathExcludedPoint;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathExcludedPointServiceResponse {

  private String pointId;
  private String reason;
  private OffsetDateTime clientTs;

  @Builder
  private SearchPathExcludedPointServiceResponse(
      String pointId, String reason, OffsetDateTime clientTs) {
    this.pointId = pointId;
    this.reason = reason;
    this.clientTs = clientTs;
  }

  public static SearchPathExcludedPointServiceResponse from(SearchPathExcludedPoint point) {
    return SearchPathExcludedPointServiceResponse.builder()
        .pointId(point.getPointId())
        .reason(point.getReason())
        .clientTs(point.getClientTs())
        .build();
  }
}
