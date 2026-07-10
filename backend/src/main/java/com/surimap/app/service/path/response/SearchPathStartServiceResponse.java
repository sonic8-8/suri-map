package com.surimap.app.service.path.response;

import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStartServiceResponse {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private UUID policePhoneId;
  private UUID accountId;
  private long version;
  private SearchPathStatus status;

  @Builder
  private SearchPathStartServiceResponse(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID policePhoneId,
      UUID accountId,
      long version,
      SearchPathStatus status) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
    this.version = version;
    this.status = status;
  }

  public static SearchPathStartServiceResponse from(SearchPath path) {
    return SearchPathStartServiceResponse.builder()
        .id(path.getId())
        .incidentId(path.getIncidentId())
        .opId(path.getOpId())
        .policePhoneId(path.getPolicePhoneId())
        .accountId(path.getAccountId())
        .version(path.getVersion())
        .status(path.getStatus())
        .build();
  }
}
