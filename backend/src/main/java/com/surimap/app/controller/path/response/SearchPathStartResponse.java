package com.surimap.app.controller.path.response;

import com.surimap.app.service.path.response.SearchPathStartServiceResponse;
import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStartResponse {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private UUID accountId;
  private long version;
  private SearchPathStatus status;

  @Builder
  private SearchPathStartResponse(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID accountId,
      long version,
      SearchPathStatus status) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.accountId = accountId;
    this.version = version;
    this.status = status;
  }

  public static SearchPathStartResponse from(SearchPathStartServiceResponse response) {
    return SearchPathStartResponse.builder()
        .id(response.getId())
        .incidentId(response.getIncidentId())
        .opId(response.getOpId())
        .accountId(response.getAccountId())
        .version(response.getVersion())
        .status(response.getStatus())
        .build();
  }
}
