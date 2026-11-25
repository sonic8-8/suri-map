package com.surimap.global.event;

import com.surimap.global.geometry.GeoJsonPoint;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerEventPayload implements MarkerPublishPayload {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private String status;
  private long version;
  private String type;
  private GeoJsonPoint location;
  private Instant clientTs;
  private Instant serverTs;

  @Builder
  private MarkerEventPayload(
      UUID id,
      UUID incidentId,
      UUID opId,
      String status,
      long version,
      String type,
      GeoJsonPoint location,
      Instant clientTs,
      Instant serverTs) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.status = status;
    this.version = version;
    this.type = type;
    this.location = location;
    this.clientTs = clientTs;
    this.serverTs = serverTs;
  }
}
