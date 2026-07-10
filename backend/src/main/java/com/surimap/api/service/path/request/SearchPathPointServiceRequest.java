package com.surimap.api.service.path.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathPointServiceRequest {

  private String pointId;
  private BigDecimal lon;
  private BigDecimal lat;
  private BigDecimal speedMps;
  private Integer horizontalAccuracyM;
  private OffsetDateTime clientTs;

  @Builder
  private SearchPathPointServiceRequest(
      String pointId,
      BigDecimal lon,
      BigDecimal lat,
      BigDecimal speedMps,
      Integer horizontalAccuracyM,
      OffsetDateTime clientTs) {
    this.pointId = pointId;
    this.lon = lon;
    this.lat = lat;
    this.speedMps = speedMps;
    this.horizontalAccuracyM = horizontalAccuracyM;
    this.clientTs = clientTs;
  }
}
