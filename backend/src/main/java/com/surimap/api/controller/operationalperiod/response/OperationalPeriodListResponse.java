package com.surimap.api.controller.operationalperiod.response;

import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.List;
import java.util.UUID;

public record OperationalPeriodListResponse(
    UUID currentOpId, List<OperationalPeriodListItemResponse> items) {

  public static OperationalPeriodListResponse from(
      UUID currentOpId, List<OperationalPeriodRow> rows) {
    return new OperationalPeriodListResponse(
        currentOpId, rows.stream().map(OperationalPeriodListItemResponse::from).toList());
  }
}
