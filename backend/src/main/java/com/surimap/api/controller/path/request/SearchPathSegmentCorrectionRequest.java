package com.surimap.api.controller.path.request;

import com.surimap.api.service.path.request.SearchPathSegmentCorrectionServiceRequest;
import com.surimap.domain.path.MovementType;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathSegmentCorrectionRequest {

  private MovementType movementType;
  private String reason;

  @Builder
  private SearchPathSegmentCorrectionRequest(MovementType movementType, String reason) {
    this.movementType = movementType;
    this.reason = reason;
  }

  public SearchPathSegmentCorrectionServiceRequest toServiceRequest(
      String searchPathSegmentId, UUID correctedByAccountId, String idempotencyKey) {
    return SearchPathSegmentCorrectionServiceRequest.builder()
        .searchPathSegmentId(searchPathSegmentId)
        .movementType(movementType)
        .reason(reason)
        .correctedByAccountId(correctedByAccountId)
        .idempotencyKey(idempotencyKey)
        .build();
  }
}
