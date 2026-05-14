package com.surimap.incident.controller.response;

import com.surimap.incident.service.IncidentActiveReadResults.MissingPerson;
import java.time.Instant;
import java.util.UUID;

/** 사건 상세의 active missing_person nested DTO. importedAt 같은 내부 추적값은 노출하지 않는다. */
public record IncidentDetailMissingPersonSummaryResponse(
    UUID incidentId,
    String displayName,
    String photoObjectKey,
    String photoUrl,
    String appearanceText,
    String lastSeenLocationText,
    Instant lastSeenAt) {

  public static IncidentDetailMissingPersonSummaryResponse from(MissingPerson missingPerson) {
    if (missingPerson == null) {
      return null;
    }
    return new IncidentDetailMissingPersonSummaryResponse(
        missingPerson.incidentId(),
        missingPerson.displayName(),
        missingPerson.photoObjectKey(),
        MissingPersonPhotoUrls.fromObjectKey(missingPerson.photoObjectKey()),
        missingPerson.appearanceText(),
        missingPerson.lastSeenLocationText(),
        missingPerson.lastSeenAt());
  }
}
