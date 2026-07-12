package com.surimap.domain.path;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

class SearchPathMetricsCalculatorTest {

  private final SearchPathMetricsCalculator calculator = new SearchPathMetricsCalculator();

  @Test
  void calculatesDistanceByMovementModeAndAverageSpeed() {
    Instant startedAt = Instant.parse("2026-05-18T00:00:00Z");
    Instant endedAt = Instant.parse("2026-05-18T00:01:00Z");
    SearchPath path =
        path(
            startedAt,
            endedAt,
            List.of(
                point("p1", "0.000", "0.000", "2026-05-18T00:00:00Z"),
                point("p2", "0.001", "0.000", "2026-05-18T00:00:30Z"),
                point("p3", "0.002", "0.000", "2026-05-18T00:01:00Z")),
            List.of(
                segment("vehicle", MovementType.VEHICLE, 0, 1),
                segment("foot", MovementType.FOOT, 1, 2)));

    SearchPathMetrics metrics = calculator.calculate(List.of(path), null, null);

    assertThat(metrics.getDistanceMeters()).isEqualTo(222L);
    assertThat(metrics.getDrivingDistanceMeters()).isEqualTo(111L);
    assertThat(metrics.getWalkingDistanceMeters()).isEqualTo(111L);
    assertThat(metrics.getAverageSpeedKmh()).isEqualByComparingTo(new BigDecimal("13.3"));
    assertThat(metrics.getStoppedSegmentCount()).isZero();
    assertThat(metrics.getStoppedDurationSeconds()).isZero();
  }

  @Test
  void countsZeroDistanceUnknownSegmentsAsStopped() {
    Instant startedAt = Instant.parse("2026-05-18T00:00:00Z");
    Instant endedAt = Instant.parse("2026-05-18T00:00:30Z");
    SearchPath path =
        path(
            startedAt,
            endedAt,
            List.of(
                point("p1", "126.913", "35.162", "2026-05-18T00:00:00Z"),
                point("p2", "126.913", "35.162", "2026-05-18T00:00:30Z")),
            List.of(segment("unknown", MovementType.UNKNOWN, 0, 1)));

    SearchPathMetrics metrics = calculator.calculate(List.of(path), null, null);

    assertThat(metrics.getDistanceMeters()).isZero();
    assertThat(metrics.getAverageSpeedKmh()).isEqualByComparingTo(new BigDecimal("0.0"));
    assertThat(metrics.getStoppedSegmentCount()).isEqualTo(1);
    assertThat(metrics.getStoppedDurationSeconds()).isEqualTo(30L);
  }

  @Test
  void usesProvidedDurationForAverageSpeedWhenScopeBoundsExist() {
    Instant startedAt = Instant.parse("2026-05-18T00:00:00Z");
    Instant endedAt = Instant.parse("2026-05-18T00:01:00Z");
    SearchPath path =
        path(
            startedAt,
            endedAt,
            List.of(
                point("p1", "0.000", "0.000", "2026-05-18T00:00:00Z"),
                point("p2", "0.001", "0.000", "2026-05-18T00:00:30Z"),
                point("p3", "0.002", "0.000", "2026-05-18T00:01:00Z")),
            List.of(segment("vehicle", MovementType.VEHICLE, 0, 2)));

    SearchPathMetrics metrics =
        calculator.calculate(List.of(path), startedAt, Instant.parse("2026-05-18T00:02:00Z"));

    assertThat(metrics.getDistanceMeters()).isEqualTo(222L);
    assertThat(metrics.getAverageSpeedKmh()).isEqualByComparingTo(new BigDecimal("6.7"));
  }

  @Test
  void calculatesLegacyMetricsFromPersistedGeometryAndSegmentTimes() {
    Instant startedAt = Instant.parse("2026-05-18T00:00:00Z");
    Instant endedAt = Instant.parse("2026-05-18T00:01:30Z");
    SearchPath path =
        SearchPath.builder()
            .id(UUID.fromString("81000000-0000-0000-0000-000000000001"))
            .startedAt(startedAt)
            .endedAt(endedAt)
            .status(SearchPathStatus.ENDED)
            .geometry(lineString(0.000, 0.000, 0.001, 0.000, 0.002, 0.000))
            .points(List.of())
            .segments(
                List.of(
                    legacySegment(
                        "vehicle",
                        MovementType.VEHICLE,
                        lineString(0.000, 0.000, 0.001, 0.000),
                        startedAt,
                        startedAt.plusSeconds(30)),
                    legacySegment(
                        "foot",
                        MovementType.FOOT,
                        lineString(0.001, 0.000, 0.002, 0.000),
                        startedAt.plusSeconds(30),
                        startedAt.plusSeconds(60)),
                    legacySegment(
                        "stopped",
                        MovementType.UNKNOWN,
                        lineString(0.002, 0.000, 0.002, 0.000),
                        startedAt.plusSeconds(60),
                        endedAt)))
            .build();

    SearchPathMetrics metrics = calculator.calculate(List.of(path), null, null);

    assertThat(path.getPoints()).isEmpty();
    assertThat(metrics.getDistanceMeters()).isEqualTo(222L);
    assertThat(metrics.getDrivingDistanceMeters()).isEqualTo(111L);
    assertThat(metrics.getWalkingDistanceMeters()).isEqualTo(111L);
    assertThat(metrics.getAverageSpeedKmh()).isEqualByComparingTo(new BigDecimal("8.9"));
    assertThat(metrics.getStoppedSegmentCount()).isEqualTo(1);
    assertThat(metrics.getStoppedDurationSeconds()).isEqualTo(30L);
  }

  private static SearchPath path(
      Instant startedAt, Instant endedAt, List<GpsPoint> points, List<SearchPathSegment> segments) {
    return SearchPath.builder()
        .id(UUID.fromString("81000000-0000-0000-0000-000000000001"))
        .dutyShiftId(null)
        .incidentId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"))
        .opId(UUID.fromString("88888888-8888-8888-8888-888888880001"))
        .startedAt(startedAt)
        .endedAt(endedAt)
        .status(SearchPathStatus.ENDED)
        .version(1L)
        .points(points)
        .excludedPoints(List.of())
        .segments(segments)
        .build();
  }

  private static GpsPoint point(String id, String lon, String lat, String at) {
    return GpsPoint.builder()
        .pointId(id)
        .clientTs(OffsetDateTime.parse(at))
        .lon(new BigDecimal(lon))
        .lat(new BigDecimal(lat))
        .speedMps(BigDecimal.ZERO)
        .horizontalAccuracyM(5)
        .build();
  }

  private static SearchPathSegment segment(
      String id, MovementType movementType, int startIndex, int endIndex) {
    return SearchPathSegment.builder()
        .id(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)))
        .movementType(movementType)
        .movementTypeSource(MovementTypeSource.AUTO)
        .startIndex(startIndex)
        .endIndex(endIndex)
        .build();
  }

  private static SearchPathSegment legacySegment(
      String id,
      MovementType movementType,
      LineString geometry,
      Instant startedAt,
      Instant endedAt) {
    return SearchPathSegment.builder()
        .id(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)))
        .movementType(movementType)
        .movementTypeSource(MovementTypeSource.AUTO)
        .geometry(geometry)
        .startedAt(startedAt)
        .endedAt(endedAt)
        .build();
  }

  private static LineString lineString(double... coordinates) {
    Coordinate[] points = new Coordinate[coordinates.length / 2];
    for (int i = 0; i < points.length; i++) {
      points[i] = new Coordinate(coordinates[i * 2], coordinates[i * 2 + 1]);
    }
    LineString geometry = new GeometryFactory().createLineString(points);
    geometry.setSRID(4326);
    return geometry;
  }
}
