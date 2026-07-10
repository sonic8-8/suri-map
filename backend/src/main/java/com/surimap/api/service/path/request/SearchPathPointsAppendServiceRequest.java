package com.surimap.api.service.path.request;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathPointsAppendServiceRequest {

  private UUID incidentId;
  private UUID opId;
  private UUID pathId;
  private List<SearchPathPointServiceRequest> points;
  private Long clockOffsetMs;
  private UUID policePhoneId;
  private UUID accountId;
  private String idempotencyKey;

  @Builder(toBuilder = true)
  private SearchPathPointsAppendServiceRequest(
      UUID incidentId,
      UUID opId,
      UUID pathId,
      List<SearchPathPointServiceRequest> points,
      Long clockOffsetMs,
      UUID policePhoneId,
      UUID accountId,
      String idempotencyKey) {
    this.incidentId = incidentId;
    this.opId = opId;
    this.pathId = pathId;
    this.points = points;
    this.clockOffsetMs = clockOffsetMs;
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
    this.idempotencyKey = idempotencyKey;
  }
}
