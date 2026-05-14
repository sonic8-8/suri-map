package com.surimap.api.controller.operationalperiod.response;

import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.time.Instant;
import java.util.UUID;

public record OperationalPeriodListItemResponse(
    UUID id,
    String status,
    String reason,
    int sequenceNumber,
    Instant openedAt,
    Instant endedAt,
    long version) {

  public static OperationalPeriodListItemResponse from(OperationalPeriodRow row) {
    return new OperationalPeriodListItemResponse(
        row.opId(),
        row.status(),
        row.reason(),
        row.sequenceNumber(),
        row.startedAt(),
        row.endedAt(),
        row.version());
  }
}
