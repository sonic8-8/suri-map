package com.surimap.api.controller.operationalperiod.response;

import com.surimap.operationalperiod.OperationalPeriod;
import java.util.UUID;

public record OperationalPeriodResponse(
    UUID id, UUID incidentId, String status, String reason, long version, int sequenceNumber) {

  public static OperationalPeriodResponse from(OperationalPeriod op) {
    return new OperationalPeriodResponse(
        op.getId(),
        op.getIncidentId(),
        op.getStatus(),
        op.getReason(),
        op.getVersion(),
        op.getSequenceNumber());
  }
}
