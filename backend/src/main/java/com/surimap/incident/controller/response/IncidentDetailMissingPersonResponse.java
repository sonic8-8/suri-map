package com.surimap.incident.controller.response;

import com.surimap.incident.domain.MissingPersonConsumerView;
import java.time.Instant;
import java.util.UUID;

/** GET incident detail의 missingPerson nested payload. 전체 detail API 조립은 L1-T05A에서 이어 붙인다. */
public record IncidentDetailMissingPersonResponse(
    UUID incidentId,
    String displayName,
    String photoObjectKey,
    String photoUrl,
    String appearanceText,
    String lastSeenLocationText,
    Instant lastSeenAt) {

  public static IncidentDetailMissingPersonResponse from(MissingPersonConsumerView view) {
    return from(view, MissingPersonPhotoUrl.mockStorage());
  }

  public static IncidentDetailMissingPersonResponse from(
      MissingPersonConsumerView view, MissingPersonPhotoUrl photoUrl) {
    if (view == null) {
      return null;
    }
    return new IncidentDetailMissingPersonResponse(
        view.incidentId(),
        view.displayName(),
        view.photoObjectKey(),
        photoUrl.fromObjectKey(view.photoObjectKey()),
        view.appearanceText(),
        view.lastSeenLocationText(),
        view.lastSeenAt());
  }
}
