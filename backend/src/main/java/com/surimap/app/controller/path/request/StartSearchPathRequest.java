package com.surimap.app.controller.path.request;

import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import java.time.Instant;
import java.util.UUID;

public record StartSearchPathRequest(
    UUID searchPathId, UUID incidentId, UUID opId, Instant clientTs, Integer clockOffsetMs) {

  public StartSearchPathRequest(
      UUID incidentId, UUID opId, Instant clientTs, Integer clockOffsetMs) {
    this(null, incidentId, opId, clientTs, clockOffsetMs);
  }

  public StartSearchPathServiceRequest toServiceRequest(
      UUID policePhoneId, UUID accountId, String idempotencyKey) {
    return new StartSearchPathServiceRequest(
        searchPathId, incidentId, opId, policePhoneId, accountId, clientTs, idempotencyKey);
  }
}
