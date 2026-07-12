package com.surimap.app.controller.path.request;

import com.surimap.app.service.path.request.SearchPathStartServiceRequest;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStartRequest {

  private UUID searchPathId;
  private UUID incidentId;
  private UUID opId;
  private Instant clientTs;
  private Integer clockOffsetMs;

  @Builder
  private SearchPathStartRequest(
      UUID searchPathId, UUID incidentId, UUID opId, Instant clientTs, Integer clockOffsetMs) {
    this.searchPathId = searchPathId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.clientTs = clientTs;
    this.clockOffsetMs = clockOffsetMs;
  }

  public SearchPathStartServiceRequest toServiceRequest(UUID accountId, String idempotencyKey) {
    return SearchPathStartServiceRequest.builder()
        .searchPathId(searchPathId)
        .incidentId(incidentId)
        .opId(opId)
        .accountId(accountId)
        .startedAt(clientTs)
        .clockOffsetMs(clockOffsetMs)
        .idempotencyKey(idempotencyKey)
        .build();
  }
}
