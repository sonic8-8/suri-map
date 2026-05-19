package com.surimap.opcomparison;

import com.surimap.operationalperiod.OperationalPeriod;
import java.util.List;
import java.util.UUID;

public record OpComparisonSourceSnapshot(
    UUID incidentId,
    List<OperationalPeriod> operationalPeriods,
    List<UUID> operationalPeriodIds,
    List<OpComparisonMetricsSource> metricsSources,
    String sourceDataHash) {

  public OpComparisonSourceSnapshot {
    operationalPeriods = List.copyOf(operationalPeriods);
    operationalPeriodIds = List.copyOf(operationalPeriodIds);
    metricsSources = List.copyOf(metricsSources);
  }
}
