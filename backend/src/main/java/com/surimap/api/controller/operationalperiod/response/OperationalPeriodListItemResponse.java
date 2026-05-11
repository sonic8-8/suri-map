package com.surimap.api.controller.operationalperiod.response;

import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.UUID;

public record OperationalPeriodListItemResponse(
    UUID id, String status, String reason, int sequenceNumber) {

  public static OperationalPeriodListItemResponse from(OperationalPeriodRow row) {
    return new OperationalPeriodListItemResponse(
        row.opId(), row.status(), row.reason(), row.sequenceNumber());
  }
}
