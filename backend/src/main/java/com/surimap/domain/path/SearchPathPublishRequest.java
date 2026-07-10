package com.surimap.domain.path;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathPublishRequest {

  private SearchPathEventType eventType;
  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private UUID policePhoneId;
  private UUID accountId;
  private SearchPathStatus status;
  private long version;

  @Builder
  private SearchPathPublishRequest(
      SearchPathEventType eventType,
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID policePhoneId,
      UUID accountId,
      SearchPathStatus status,
      long version) {
    this.eventType = eventType;
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.accountId = accountId;
    this.status = status;
    this.version = version;
  }
}
