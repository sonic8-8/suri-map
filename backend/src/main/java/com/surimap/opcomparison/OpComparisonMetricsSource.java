package com.surimap.opcomparison;

import com.surimap.path.SearchPathAggregate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OpComparisonMetricsSource(
    UUID operationalPeriodId,
    int sequenceNumber,
    Instant startedAt,
    Instant endedAt,
    List<SearchPathAggregate> paths,
    int markerCount,
    int handoverMemoCount) {

  public OpComparisonMetricsSource {
    Objects.requireNonNull(operationalPeriodId, "operationalPeriodId must not be null");
    paths = paths == null ? List.of() : List.copyOf(paths);
  }
}
