package com.surimap.api.controller.path.request;

import com.surimap.api.service.path.request.SearchPathPointsAppendServiceRequest;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathPointsAppendRequest {

  private UUID incidentId;
  private UUID opId;
  private UUID pathId;
  private List<SearchPathPointRequest> points;
  private Long clockOffsetMs;

  @Builder
  private SearchPathPointsAppendRequest(
      UUID incidentId,
      UUID opId,
      UUID pathId,
      List<SearchPathPointRequest> points,
      Long clockOffsetMs) {
    this.incidentId = incidentId;
    this.opId = opId;
    this.pathId = pathId;
    this.points = points;
    this.clockOffsetMs = clockOffsetMs;
  }

  public SearchPathPointsAppendServiceRequest toServiceRequest(
      UUID policePhoneId, UUID accountId, String idempotencyKey) {
    return SearchPathPointsAppendServiceRequest.builder()
        .incidentId(incidentId)
        .opId(opId)
        .pathId(pathId)
        .points(
            points == null
                ? null
                : points.stream().map(SearchPathPointRequest::toServiceRequest).toList())
        .clockOffsetMs(clockOffsetMs)
        .policePhoneId(policePhoneId)
        .accountId(accountId)
        .idempotencyKey(idempotencyKey)
        .build();
  }
}
