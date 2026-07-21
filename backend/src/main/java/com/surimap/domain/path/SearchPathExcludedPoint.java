package com.surimap.domain.path;

import java.math.BigDecimal;
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
  private BigDecimal lon;
  private BigDecimal lat;
  private BigDecimal speedMps;
  private Integer horizontalAccuracyM;
  private String locationProvider;
  private Long elapsedRealtimeNanos;
  private Instant createdAt;
  private Instant updatedAt;

  @Builder(toBuilder = true)
  private SearchPathExcludedPoint(
      UUID id,
      UUID searchPathId,
      String pointId,
      String reason,
      OffsetDateTime clientTs,
      BigDecimal lon,
      BigDecimal lat,
      BigDecimal speedMps,
      Integer horizontalAccuracyM,
      String locationProvider,
      Long elapsedRealtimeNanos,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.searchPathId = searchPathId;
    this.pointId = pointId;
    this.reason = reason;
    this.clientTs = clientTs;
    this.lon = lon;
    this.lat = lat;
    this.speedMps = speedMps;
    this.horizontalAccuracyM = horizontalAccuracyM;
    this.locationProvider = locationProvider;
    this.elapsedRealtimeNanos = elapsedRealtimeNanos;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }
}
