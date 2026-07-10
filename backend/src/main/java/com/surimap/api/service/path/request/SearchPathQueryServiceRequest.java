package com.surimap.api.service.path.request;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathQueryServiceRequest {

  private UUID incidentId;
  private UUID opId;
  private UUID policePhoneId;
  private UUID accountId;

  @Builder
  private SearchPathQueryServiceRequest(
      UUID incidentId, UUID opId, UUID policePhoneId, UUID accountId) {
    this.incidentId = incidentId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
  }
}
