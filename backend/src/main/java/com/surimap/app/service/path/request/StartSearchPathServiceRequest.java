package com.surimap.app.service.path.request;

import java.time.Instant;
import java.util.UUID;

public record StartSearchPathServiceRequest(
    UUID searchPathId,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    Instant startedAt,
    String idempotencyKey) {

  public StartSearchPathServiceRequest(
      UUID incidentId, UUID opId, UUID policePhoneId, Instant startedAt, String idempotencyKey) {
    this(null, incidentId, opId, policePhoneId, startedAt, idempotencyKey);
  }
}
