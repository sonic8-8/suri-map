package com.surimap.app.service.path.request;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStatusUpdateServiceRequest {

  private UUID searchPathId;
  private UUID policePhoneId;
  private UUID accountId;
  private SearchPathLifecycleAction action;
  private Instant clientTs;
  private Integer clockOffsetMs;
  private String idempotencyKey;

  @Builder(toBuilder = true)
  private SearchPathStatusUpdateServiceRequest(
      UUID searchPathId,
      UUID policePhoneId,
      UUID accountId,
      SearchPathLifecycleAction action,
      Instant clientTs,
      Integer clockOffsetMs,
      String idempotencyKey) {
    this.searchPathId = searchPathId;
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
    this.action = action;
    this.clientTs = clientTs;
    this.clockOffsetMs = clockOffsetMs;
    this.idempotencyKey = idempotencyKey;
  }
}
