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
    String locationLabel,
    String policePhoneName)
    implements MarkerPublishPayload {

  public MarkerNotificationPublishRequestPayload {
    recipientAccountIds =
        recipientAccountIds == null ? List.of() : List.copyOf(recipientAccountIds);
    recipientPolicePhoneIds =
        recipientPolicePhoneIds == null ? List.of() : List.copyOf(recipientPolicePhoneIds);
  }

  public MarkerNotificationPublishRequestPayload(
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
      String locationLabel) {
    this(
        id,
        markerId,
        incidentId,
        opId,
        policePhoneId,
        status,
        version,
        type,
        recipientPolicy,
        recipientAccountIds,
        recipientPolicePhoneIds,
        markerType,
        locationLabel,
        null);
  }
}
