package com.surimap.maparea.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SearchAreaQuery.byIncident / byOp 공통 필터 (S2.json §service_contracts).
 *
 * <p>opId는 byIncident 전용. byOp 호출 시에는 무시된다. includeCancelled 기본값은 false.
 */
public record SearchAreaFilters(
    List<String> status,
    UUID opId,
    List<BigDecimal> bbox,
    Long minVersion,
    Instant updatedAfter,
    boolean includeCancelled) {

  public static SearchAreaFilters empty() {
    return new SearchAreaFilters(null, null, null, null, null, false);
  }
}
