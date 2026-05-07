package com.surimap.marker.dto;

import java.util.List;
import java.util.UUID;

public record MarkerNotificationPublishRequestPayload(
    UUID id,
    UUID markerId,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    String status,
    long version,
    String type,
    String recipientPolicy,
    List<String> recipientAccountIds,
    List<String> recipientPolicePhoneIds,
    String markerType,
    String locationLabel)
    implements MarkerPublishPayload {

  public MarkerNotificationPublishRequestPayload {
    recipientAccountIds =
        recipientAccountIds == null ? List.of() : List.copyOf(recipientAccountIds);
    recipientPolicePhoneIds =
        recipientPolicePhoneIds == null ? List.of() : List.copyOf(recipientPolicePhoneIds);
  }
}
