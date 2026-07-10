package com.surimap.domain.path.validation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GpsPathPoint {

  private String pointId;
  private OffsetDateTime clientTs;
  private BigDecimal lon;
  private BigDecimal lat;
  private BigDecimal speedMps;
  private Integer horizontalAccuracyM;

  @Builder
  private GpsPathPoint(
      String pointId,
      OffsetDateTime clientTs,
      BigDecimal lon,
      BigDecimal lat,
      BigDecimal speedMps,
      Integer horizontalAccuracyM) {
    this.pointId = pointId;
    this.clientTs = clientTs;
    this.lon = lon;
    this.lat = lat;
    this.speedMps = speedMps;
    this.horizontalAccuracyM = horizontalAccuracyM;
  }
}
