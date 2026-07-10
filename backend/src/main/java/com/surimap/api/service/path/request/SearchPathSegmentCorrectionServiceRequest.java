package com.surimap.api.service.path.request;

import com.surimap.domain.path.MovementType;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathSegmentCorrectionServiceRequest {

  private String searchPathSegmentId;
  private MovementType movementType;
  private String reason;
  private UUID correctedByAccountId;
  private String idempotencyKey;

  @Builder(toBuilder = true)
  private SearchPathSegmentCorrectionServiceRequest(
      String searchPathSegmentId,
      MovementType movementType,
      String reason,
      UUID correctedByAccountId,
      String idempotencyKey) {
    this.searchPathSegmentId = searchPathSegmentId;
    this.movementType = movementType;
    this.reason = reason;
    this.correctedByAccountId = correctedByAccountId;
    this.idempotencyKey = idempotencyKey;
  }
}
