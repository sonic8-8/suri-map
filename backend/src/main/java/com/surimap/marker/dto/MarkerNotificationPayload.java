package com.surimap.marker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerNotificationPayload implements MarkerPublishPayload {

  private UUID id;
  private UUID markerId;
  private UUID incidentId;
  private UUID opId;
  private UUID policePhoneId;
  private String status;
  private long version;
  private String type;
  private String recipientPolicy;
  private List<String> recipientAccountIds = List.of();
  private List<String> recipientPolicePhoneIds = List.of();
  private String markerType;
  private String locationLabel;
  private String policePhoneName;
  private Instant clientTs;

  @Builder
  private MarkerNotificationPayload(
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
      String policePhoneName,
      Instant clientTs) {
    this.id = id;
    this.markerId = markerId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.status = status;
    this.version = version;
    this.type = type;
    this.recipientPolicy = recipientPolicy;
    this.recipientAccountIds =
        recipientAccountIds == null ? List.of() : List.copyOf(recipientAccountIds);
    this.recipientPolicePhoneIds =
        recipientPolicePhoneIds == null ? List.of() : List.copyOf(recipientPolicePhoneIds);
    this.markerType = markerType;
    this.locationLabel = locationLabel;
    this.policePhoneName = policePhoneName;
    this.clientTs = clientTs;
  }
}
