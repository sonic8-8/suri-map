package com.surimap.domain.path.validation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

final class GpsPointValidationFixtures {

  static final String NORMAL_PATH_ALIAS = "path-precinct-mixed-001";
  static final String NORMAL_POLICE_PHONE_CODE = "dev-precinct-car-01";
  static final String VEHICLE_SEGMENT_ALIAS = "seg-precinct-vehicle-001";
  static final String FOOT_SEGMENT_ALIAS = "seg-precinct-foot-001";

  static final List<GpsPointFixture> NORMAL_POINTS =
      List.of(
          point(
              "gps-precinct-001", "2026-04-28T09:00:00+09:00", "126.913000", "35.162000", 13.5, 5),
          point(
              "gps-precinct-002", "2026-04-28T09:00:05+09:00", "126.913650", "35.162180", 12.8, 5),
          point(
              "gps-precinct-003", "2026-04-28T09:00:10+09:00", "126.914300", "35.162360", 11.9, 5),
          point("gps-precinct-004", "2026-04-28T09:00:15+09:00", "126.914850", "35.162540", 9.8, 5),
          point("gps-precinct-005", "2026-04-28T09:00:20+09:00", "126.915000", "35.162700", 1.6, 5),
          point("gps-precinct-006", "2026-04-28T09:00:25+09:00", "126.915080", "35.162880", 1.3, 5),
          point("gps-precinct-007", "2026-04-28T09:00:30+09:00", "126.915160", "35.163050", 1.1, 5),
          point(
              "gps-precinct-008", "2026-04-28T09:00:35+09:00", "126.915250", "35.163120", 1.4, 5));

  static final List<PathSegmentFixture> NORMAL_SEGMENTS =
      List.of(
          new PathSegmentFixture(
              VEHICLE_SEGMENT_ALIAS, "VEHICLE", 0, 3, "gps-precinct-001", "gps-precinct-004"),
          new PathSegmentFixture(
              FOOT_SEGMENT_ALIAS, "FOOT", 4, 7, "gps-precinct-005", "gps-precinct-008"));

  static final StructuralFailureFixture BATCH_LIMIT_EXCEEDED =
      new StructuralFailureFixture(
          "gps-batch-over-limit-121",
          IntStream.rangeClosed(1, 121)
              .mapToObj(
                  index ->
                      point(
                          "gps-over-limit-%03d".formatted(index),
                          "2026-04-28T09:%02d:%02d+09:00"
                              .formatted((index - 1) / 12, ((index - 1) % 12) * 5),
                          "126.%06d".formatted(913000 + index - 1),
                          "35.%06d".formatted(162000 + index - 1),
                          2.0,
                          5))
              .toList(),
          "invalid_geometry",
          "points maxItems=120");

  static final List<GpsPointFixture> OUTSIDE_SEARCH_AREA_POINTS =
      List.of(
          point(
              "gps-outside-001",
              "2026-04-28T09:05:00+09:00",
              "127.200000",
              "35.163100",
              3.0,
              5),
          point(
              "gps-outside-002",
              "2026-04-28T09:05:05+09:00",
              "127.200100",
              "35.163150",
              3.0,
              5));

  static final StructuralFailureFixture COORDINATE_LAT_LON_SWAPPED =
      new StructuralFailureFixture(
          "coord-latlon-swapped",
          List.of(
              point(
                  "gps-swapped-001",
                  "2026-04-28T09:06:00+09:00",
                  "35.163100",
                  "126.913400",
                  3.0,
                  5),
              point(
                  "gps-swapped-002",
                  "2026-04-28T09:06:05+09:00",
                  "35.163300",
                  "126.913600",
                  3.0,
                  5)),
          "invalid_geometry",
          "lon/lat order must be EPSG:4326");

  static final StructuralFailureFixture NON_MONOTONIC_CLIENT_TS =
      new StructuralFailureFixture(
          "gps-client-ts-non-monotonic",
          List.of(
              point("gps-ts-001", "2026-04-28T09:07:00+09:00", "126.913000", "35.162000", 3.0, 5),
              point("gps-ts-002", "2026-04-28T09:06:55+09:00", "126.913100", "35.162100", 3.0, 5)),
          "invalid_geometry",
          "clientTs strict monotonic");

  static final StructuralFailureFixture NULL_COORDINATE =
      new StructuralFailureFixture(
          "point-null-nan",
          List.of(
              point("gps-null-001", "2026-04-28T09:08:00+09:00", null, "35.162000", 3.0, 5),
              point(
                  "gps-null-002", "2026-04-28T09:08:05+09:00", "126.913100", "35.162100", 3.0, 5)),
          "invalid_geometry",
          "null or NaN coordinate is a structural failure");

  static final StructuralFailureFixture PRECISION_OVER_SIX_DP =
      new StructuralFailureFixture(
          "precision-over-6dp",
          List.of(
              point(
                  "gps-precision-001",
                  "2026-04-28T09:09:00+09:00",
                  "126.9134007",
                  "35.1631007",
                  3.0,
                  5),
              point(
                  "gps-precision-002",
                  "2026-04-28T09:09:05+09:00",
                  "126.9136007",
                  "35.1633007",
                  3.0,
                  5)),
          "invalid_geometry",
          "precision exceeds 6 decimal places");

  static final QualityFailureFixture LOW_ACCURACY =
      new QualityFailureFixture(
          "gps-low-quality-accuracy-001",
          QualityFailureReason.LOW_ACCURACY,
          List.of(
              point(
                  "gps-quality-accuracy-001",
                  "2026-04-28T09:10:00+09:00",
                  "126.913000",
                  "35.162000",
                  3.0,
                  51)),
          OffsetDateTime.parse("2026-04-28T09:10:00+09:00"),
          "horizontalAccuracyM > 50",
          BigDecimal.valueOf(51),
          BigDecimal.valueOf(GpsPointValidationCriteria.MAX_HORIZONTAL_ACCURACY_METERS),
          ViolationDirection.ABOVE_MAX,
          "excludedPoints");

  static final QualityFailureFixture TIMESTAMP_SKEW =
      new QualityFailureFixture(
          "gps-low-quality-skew-001",
          QualityFailureReason.CLOCK_SKEW,
          List.of(
              point(
                  "gps-quality-skew-001",
                  "2026-04-28T09:10:35+09:00",
                  "126.913100",
                  "35.162100",
                  3.0,
                  5)),
          OffsetDateTime.parse("2026-04-28T09:10:00+09:00"),
          "timestampSkewSec > 30",
          BigDecimal.valueOf(35),
          BigDecimal.valueOf(GpsPointValidationCriteria.MAX_TIMESTAMP_SKEW_SECONDS),
          ViolationDirection.ABOVE_MAX,
          "excludedPoints");

  static final QualityFailureFixture NEGATIVE_SPEED =
      new QualityFailureFixture(
          "gps-low-quality-speed-negative-001",
          QualityFailureReason.INVALID_SPEED,
          List.of(
              point(
                  "gps-quality-speed-negative-001",
                  "2026-04-28T09:10:10+09:00",
                  "126.913200",
                  "35.162200",
                  -1.0,
                  5)),
          OffsetDateTime.parse("2026-04-28T09:10:10+09:00"),
          "speedMps < 0",
          BigDecimal.valueOf(-1.0),
          BigDecimal.ZERO,
          ViolationDirection.BELOW_MIN,
          "excludedPoints");

  static final QualityFailureFixture EXCESSIVE_SPEED =
      new QualityFailureFixture(
          "gps-low-quality-speed-over-001",
          QualityFailureReason.INVALID_SPEED,
          List.of(
              point(
                  "gps-quality-speed-over-001",
                  "2026-04-28T09:10:10+09:00",
                  "126.913200",
                  "35.162200",
                  46.0,
                  5)),
          OffsetDateTime.parse("2026-04-28T09:10:10+09:00"),
          "speedMps > 45",
          BigDecimal.valueOf(46),
          BigDecimal.valueOf(GpsPointValidationCriteria.MAX_SPEED_METERS_PER_SECOND),
          ViolationDirection.ABOVE_MAX,
          "excludedPoints");

  static final QualityFailureFixture DISTANCE_JUMP =
      new QualityFailureFixture(
          "gps-low-quality-jump-001",
          QualityFailureReason.DISTANCE_JUMP,
          List.of(
              point(
                  "gps-quality-jump-prev-001",
                  "2026-04-28T09:10:10+09:00",
                  "126.913200",
                  "35.162200",
                  3.0,
                  5),
              point(
                  "gps-quality-jump-001",
                  "2026-04-28T09:10:15+09:00",
                  "126.915810",
                  "35.162200",
                  3.0,
                  5)),
          OffsetDateTime.parse("2026-04-28T09:10:15+09:00"),
          "distance > 200m between 5-second samples",
          BigDecimal.valueOf(237),
          BigDecimal.valueOf(GpsPointValidationCriteria.MAX_DISTANCE_JUMP_METERS_PER_FIVE_SECONDS),
          ViolationDirection.ABOVE_MAX,
          "excludedPoints");

  static final List<StructuralFailureFixture> STRUCTURAL_FAILURE_FIXTURES =
      List.of(
          BATCH_LIMIT_EXCEEDED,
          COORDINATE_LAT_LON_SWAPPED,
          NON_MONOTONIC_CLIENT_TS,
          NULL_COORDINATE,
          PRECISION_OVER_SIX_DP);

  static final List<QualityFailureFixture> QUALITY_FAILURE_FIXTURES =
      List.of(LOW_ACCURACY, TIMESTAMP_SKEW, NEGATIVE_SPEED, EXCESSIVE_SPEED, DISTANCE_JUMP);

  private GpsPointValidationFixtures() {}

  private static GpsPointFixture point(
      String pointId,
      String clientTs,
      String lon,
      String lat,
      double speedMps,
      Integer horizontalAccuracyM) {
    return new GpsPointFixture(
        pointId,
        OffsetDateTime.parse(clientTs),
        decimalOrNull(lon),
        decimalOrNull(lat),
        BigDecimal.valueOf(speedMps),
        horizontalAccuracyM);
  }

  private static BigDecimal decimalOrNull(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  record GpsPointFixture(
      String pointId,
      OffsetDateTime clientTs,
      BigDecimal lon,
      BigDecimal lat,
      BigDecimal speedMps,
      Integer horizontalAccuracyM) {}

  record PathSegmentFixture(
      String segmentId,
      String type,
      int startIndex,
      int endIndex,
      String startPointId,
      String endPointId) {}

  record StructuralFailureFixture(
      String name, List<GpsPointFixture> points, String publicError, String rule) {}

  record QualityFailureFixture(
      String name,
      QualityFailureReason expectedReason,
      List<GpsPointFixture> points,
      OffsetDateTime serverReceivedAt,
      String rule,
      BigDecimal measuredValue,
      BigDecimal threshold,
      ViolationDirection violationDirection,
      String expectedDisposition) {
    QualityFailureFixture {
      if (points.isEmpty()) {
        throw new IllegalArgumentException("points must not be empty");
      }
    }

    GpsPointFixture point() {
      return points.get(points.size() - 1);
    }
  }

  enum ViolationDirection {
    BELOW_MIN,
    ABOVE_MAX
  }

  enum QualityFailureReason {
    LOW_ACCURACY,
    CLOCK_SKEW,
    INVALID_SPEED,
    DISTANCE_JUMP
  }
}
