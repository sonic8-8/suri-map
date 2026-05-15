package com.surimap.marker.dto;

import java.util.List;
import java.util.UUID;

public record MarkerCreateResponse(
    UUID id,
    UUID incidentId,
    UUID opId,
    UUID policePhoneId,
    String status,
    long version,
    List<MarkerCreatePhotoResponse> photos) {

  public MarkerCreateResponse(
      UUID id, UUID incidentId, UUID opId, UUID policePhoneId, String status, long version) {
    this(id, incidentId, opId, policePhoneId, status, version, List.of());
  }

  public MarkerCreateResponse {
    photos = photos == null ? List.of() : List.copyOf(photos);
  }
}
