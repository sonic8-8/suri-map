package com.surimap.opcomparison;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.path.MovementType;
import com.surimap.path.MovementTypeSource;
import com.surimap.path.SearchPathAggregate;
import com.surimap.path.SearchPathPoint;
import com.surimap.path.SearchPathSegment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpComparisonMetricsCalculatorTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");

  private final OpComparisonMetricsCalculator calculator = new OpComparisonMetricsCalculator();

  @Test
  void calculatesDeterministicMetricsForEachOperationalPeriod() {
    OpComparisonMetricsSource movingOp =
        new OpComparisonMetricsSource(
            OP1_ID,
            1,
            Instant.parse("2026-05-18T00:00:00Z"),
            Instant.parse("2026-05-18T00:02:00Z"),
            List.of(
                path(
                    OP1_ID,
                    List.of(
                        point("p1", "0.000", "0.000", "2026-05-18T00:00:00Z"),
                        point("p2", "0.001", "0.000", "2026-05-18T00:01:00Z"),
                        point("p3", "0.002", "0.000", "2026-05-18T00:02:00Z")),
                    List.of(
                        segment("vehicle", MovementType.VEHICLE, 0, 1),
                        segment("foot", MovementType.FOOT, 1, 2)))),
            2,
            1);
    OpComparisonMetricsSource stoppedOp =
        new OpComparisonMetricsSource(
            OP2_ID,
            2,
            Instant.parse("2026-05-18T01:00:00Z"),
            Instant.parse("2026-05-18T01:10:00Z"),
            List.of(
                path(
                    OP2_ID,
                    List.of(
                        point("s1", "126.913", "35.162", "2026-05-18T01:00:00Z"),
                        point("s2", "126.913", "35.162", "2026-05-18T01:10:00Z")),
                    List.of(segment("unknown", MovementType.UNKNOWN, 0, 1)))),
            0,
            3);

    List<OpComparisonOperationalPeriodMetrics> metrics =
        calculator.calculate(List.of(movingOp, stoppedOp));

    assertThat(metrics).hasSize(2);
    OpComparisonOperationalPeriodMetrics movingMetrics = metrics.get(0);
    assertThat(movingMetrics.operationalPeriodId()).isEqualTo(OP1_ID);
    assertThat(movingMetrics.sequenceNumber()).isEqualTo(1);
    assertThat(movingMetrics.pathDistanceMeters()).isEqualTo(222L);
    assertThat(movingMetrics.drivingDistanceMeters()).isEqualTo(111L);
    assertThat(movingMetrics.walkingDistanceMeters()).isEqualTo(111L);
    assertThat(movingMetrics.walkingRatioPercent()).isEqualTo(50);
    assertThat(movingMetrics.averageSpeedKmh()).isEqualByComparingTo(new BigDecimal("6.7"));
    assertThat(movingMetrics.stoppedSegmentCount()).isZero();
    assertThat(movingMetrics.stoppedDurationSeconds()).isZero();
    assertThat(movingMetrics.markerCount()).isEqualTo(2);
    assertThat(movingMetrics.handoverMemoCount()).isEqualTo(1);

    OpComparisonOperationalPeriodMetrics stoppedMetrics = metrics.get(1);
    assertThat(stoppedMetrics.operationalPeriodId()).isEqualTo(OP2_ID);
    assertThat(stoppedMetrics.sequenceNumber()).isEqualTo(2);
    assertThat(stoppedMetrics.pathDistanceMeters()).isZero();
    assertThat(stoppedMetrics.walkingRatioPercent()).isZero();
    assertThat(stoppedMetrics.averageSpeedKmh()).isEqualByComparingTo(new BigDecimal("0.0"));
    assertThat(stoppedMetrics.stoppedSegmentCount()).isEqualTo(1);
    assertThat(stoppedMetrics.stoppedDurationSeconds()).isEqualTo(600L);
    assertThat(stoppedMetrics.markerCount()).isZero();
    assertThat(stoppedMetrics.handoverMemoCount()).isEqualTo(3);
  }

  @Test
  void returnsZeroMetricsForEmptyOperationalPeriodSource() {
    OpComparisonMetricsSource source =
        new OpComparisonMetricsSource(
            OP1_ID,
            1,
            Instant.parse("2026-05-18T00:00:00Z"),
            Instant.parse("2026-05-18T00:10:00Z"),
            List.of(),
            0,
            0);

    OpComparisonOperationalPeriodMetrics metrics = calculator.calculate(List.of(source)).get(0);

    assertThat(metrics.operationalPeriodId()).isEqualTo(OP1_ID);
    assertThat(metrics.pathDistanceMeters()).isZero();
    assertThat(metrics.walkingDistanceMeters()).isZero();
    assertThat(metrics.drivingDistanceMeters()).isZero();
    assertThat(metrics.walkingRatioPercent()).isZero();
    assertThat(metrics.averageSpeedKmh()).isEqualByComparingTo(new BigDecimal("0.0"));
    assertThat(metrics.stoppedSegmentCount()).isZero();
    assertThat(metrics.stoppedDurationSeconds()).isZero();
  }

  private static SearchPathAggregate path(
      UUID opId, List<SearchPathPoint> points, List<SearchPathSegment> segments) {
    SearchPathAggregate path =
        new SearchPathAggregate(
            UUID.randomUUID(),
            INCIDENT_ID,
            opId,
            POLICE_PHONE_ID);
    path.appendAcceptedPoints(points);
    path.replaceSegments(segments);
    return path;
  }

  private static SearchPathPoint point(String id, String lon, String lat, String at) {
    return new SearchPathPoint(
        id,
        OffsetDateTime.parse(at),
        new BigDecimal(lon),
        new BigDecimal(lat),
        BigDecimal.ZERO,
        5);
  }

  private static SearchPathSegment segment(
      String id, MovementType movementType, int startIndex, int endIndex) {
    return new SearchPathSegment(
        id, 1L, movementType, MovementTypeSource.AUTO, startIndex, endIndex, null, null, null, null);
  }
}
