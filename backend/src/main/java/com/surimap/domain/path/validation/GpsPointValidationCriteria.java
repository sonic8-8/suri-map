package com.surimap.domain.path.validation;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class GpsPointValidationCriteria {

  public static final int MIN_POINTS_PER_BATCH = 2;
  public static final int MAX_POINTS_PER_BATCH = 120;
  public static final int MAX_HORIZONTAL_ACCURACY_METERS = 50;
  public static final int MAX_TIMESTAMP_SKEW_SECONDS = 30;
  public static final int MAX_SPEED_METERS_PER_SECOND = 45;
  public static final int MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS = 200;
  public static final int CANONICAL_COORDINATE_SCALE = 6;

  public static final GeoEnvelope HARNESS_ENVELOPE =
      GeoEnvelope.builder()
          .minLon(126.647507)
          .minLat(35.052595)
          .maxLon(127.017482)
          .maxLat(35.256837)
          .build();

  private GpsPointValidationCriteria() {}

  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class GeoEnvelope {

    private double minLon;
    private double minLat;
    private double maxLon;
    private double maxLat;

    @Builder
    private GeoEnvelope(double minLon, double minLat, double maxLon, double maxLat) {
      this.minLon = minLon;
      this.minLat = minLat;
      this.maxLon = maxLon;
      this.maxLat = maxLat;
    }
  }
}
