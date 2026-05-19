package com.surimap.opcomparison;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OpComparisonEvidenceBuilder {

  public OpComparisonEvidencePackage build(
      UUID comparisonId,
      UUID incidentId,
      List<OpComparisonOperationalPeriodMetrics> metrics,
      OpComparisonThresholdResult thresholdResult) {
    List<OpComparisonOperationalPeriodEvidence> operationalPeriods =
        (metrics == null ? List.<OpComparisonOperationalPeriodMetrics>of() : metrics).stream()
            .map(this::toOperationalPeriodEvidence)
            .toList();
    List<OpComparisonDiffFact> diffFacts =
        thresholdResult == null ? List.of() : thresholdResult.diffFacts();
    List<OpComparisonRegionEvidence> regionFacts =
        thresholdResult == null
            ? List.of()
            : thresholdResult.regionFacts().stream().map(this::toRegionEvidence).toList();
    return new OpComparisonEvidencePackage(
        comparisonId, incidentId, operationalPeriods, diffFacts, regionFacts);
  }

  private OpComparisonOperationalPeriodEvidence toOperationalPeriodEvidence(
      OpComparisonOperationalPeriodMetrics metrics) {
    return new OpComparisonOperationalPeriodEvidence(
        metrics.operationalPeriodId(),
        metrics.sequenceNumber(),
        metrics.startedAt(),
        metrics.endedAt(),
        new OpComparisonMetricsEvidence(
            metrics.pathDistanceMeters(),
            metrics.walkingDistanceMeters(),
            metrics.drivingDistanceMeters(),
            metrics.walkingRatioPercent(),
            metrics.averageSpeedKmh(),
            metrics.stoppedSegmentCount(),
            metrics.stoppedDurationSeconds(),
            metrics.markerCount(),
            metrics.handoverMemoCount()));
  }

  private OpComparisonRegionEvidence toRegionEvidence(OpComparisonRegionFact fact) {
    List<OpComparisonRegionOccupancy> occupancies = orderedOccupancies(fact);
    Map<UUID, Instant> firstPassTimes = new LinkedHashMap<>();
    Map<UUID, Long> durationSeconds = new LinkedHashMap<>();
    for (OpComparisonRegionOccupancy occupancy : occupancies) {
      firstPassTimes.put(occupancy.operationalPeriodId(), occupancy.firstObservedAt());
      durationSeconds.put(occupancy.operationalPeriodId(), occupancy.durationSeconds());
    }
    return new OpComparisonRegionEvidence(
        fact.factId(),
        fact.type(),
        fact.operationalPeriodIds(),
        fact.areaSquareMeters(),
        firstPassTimes,
        durationSeconds);
  }

  private List<OpComparisonRegionOccupancy> orderedOccupancies(OpComparisonRegionFact fact) {
    Map<UUID, Integer> order = new LinkedHashMap<>();
    for (int i = 0; i < fact.operationalPeriodIds().size(); i++) {
      order.put(fact.operationalPeriodIds().get(i), i);
    }
    return fact.occupancies().stream()
        .sorted(
            Comparator.comparingInt(
                occupancy -> order.getOrDefault(occupancy.operationalPeriodId(), Integer.MAX_VALUE)))
        .toList();
  }
}
