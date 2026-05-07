package com.surimap.app.controller.path.request;

import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import java.time.Instant;
import java.util.UUID;

public record StartSearchPathRequest(
    UUID incidentId, UUID opId, Instant clientTs, Integer clockOffsetMs) {

  public StartSearchPathServiceRequest toServiceRequest(UUID policePhoneId, String idempotencyKey) {
    return new StartSearchPathServiceRequest(incidentId, opId, policePhoneId, clientTs, idempotencyKey);
  }
}
