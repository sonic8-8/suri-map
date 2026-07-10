package com.surimap.api.controller.path.response;

import com.surimap.api.service.path.response.SearchPathExcludedPointServiceResponse;
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

  public static SearchPathExcludedPointResponse from(SearchPathExcludedPointServiceResponse point) {
    return SearchPathExcludedPointResponse.builder()
        .pointId(point.getPointId())
        .reason(point.getReason())
        .clientTs(point.getClientTs())
        .build();
  }
}
