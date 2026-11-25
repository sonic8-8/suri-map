package com.surimap.global.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private String status;
  private long version;
  private String type;
  private String recipientPolicy;
  private List<String> recipientAccountIds = List.of();
  private String markerType;
  private String locationLabel;
  private String createdByAccountDisplayName;
  private Instant clientTs;

  @Builder
  private MarkerNotificationPayload(
      UUID id,
      UUID markerId,
      UUID incidentId,
      UUID opId,
      String status,
      long version,
      String type,
      String recipientPolicy,
      List<String> recipientAccountIds,
      String markerType,
      String locationLabel,
      String createdByAccountDisplayName,
      Instant clientTs) {
    this.id = id;
    this.markerId = markerId;
    this.incidentId = incidentId;
    this.opId = opId;
    this.status = status;
    this.version = version;
    this.type = type;
    this.recipientPolicy = recipientPolicy;
    this.recipientAccountIds =
        recipientAccountIds == null ? List.of() : List.copyOf(recipientAccountIds);
    this.markerType = markerType;
    this.locationLabel = locationLabel;
    this.createdByAccountDisplayName = createdByAccountDisplayName;
    this.clientTs = clientTs;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("type", type);
    values.put("id", Objects.toString(id, null));
    values.put("markerId", Objects.toString(markerId, null));
    values.put("incidentId", Objects.toString(incidentId, null));
    values.put("opId", Objects.toString(opId, null));
    values.put("status", status);
    values.put("version", version);
    values.put("recipientPolicy", recipientPolicy);
    values.put("recipientAccountIds", recipientAccountIds);
    values.put("markerType", markerType);
    values.put("locationLabel", locationLabel);
    values.put("createdByAccountDisplayName", createdByAccountDisplayName);
    if (clientTs != null) {
      values.put("clientTs", clientTs.toString());
    }
    return values;
  }
}
