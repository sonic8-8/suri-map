package com.surimap.incident.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** S1-1이 incident detail과 S7 package 입력에 제공하는 active missing_person projection. */
public record MissingPersonConsumerView(
    UUID incidentId,
    String displayName,
    String photoObjectKey,
    String appearanceText,
    String lastSeenLocationText,
    Instant lastSeenAt) {

  public MissingPersonConsumerView {
    Objects.requireNonNull(incidentId, "incidentId must not be null");
    Objects.requireNonNull(displayName, "displayName must not be null");
  }

  public static MissingPersonConsumerView from(MissingPersonRecord record) {
    Objects.requireNonNull(record, "record must not be null");
    return new MissingPersonConsumerView(
        record.getIncidentId(),
        record.getDisplayName(),
        record.getPhotoObjectKey(),
        record.getAppearanceText(),
        record.getLastSeenLocationText(),
        record.getLastSeenAt());
  }
}
