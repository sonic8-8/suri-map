package com.surimap.app.service.path.request;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStartServiceRequest {

  private UUID searchPathId;
  private UUID incidentId;
  private UUID opId;
  private UUID accountId;
  private Instant startedAt;
  private Integer clockOffsetMs;
  private String idempotencyKey;

  @Builder(toBuilder = true)
  private SearchPathStartServiceRequest(
      UUID searchPathId,
      UUID incidentId,
      UUID opId,
      UUID accountId,
      Instant startedAt,
      Integer clockOffsetMs,
      String idempotencyKey) {
    this.searchPathId = searchPathId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.accountId = accountId;
    this.startedAt = startedAt;
    this.clockOffsetMs = clockOffsetMs;
    this.idempotencyKey = idempotencyKey;
  }
}
