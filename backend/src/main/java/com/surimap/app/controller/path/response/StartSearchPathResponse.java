package com.surimap.app.controller.path.response;

import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;

public record StartSearchPathResponse(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    UUID accountId,
    long version,
    SearchPathStatus status) {

  public static StartSearchPathResponse from(SearchPath path) {
    return new StartSearchPathResponse(
        path.id(),
        path.incidentId(),
        path.opId(),
        path.policePhoneId(),
        path.accountId(),
        path.version(),
        path.status());
  }
}
