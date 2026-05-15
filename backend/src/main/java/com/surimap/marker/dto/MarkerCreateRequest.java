package com.surimap.marker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarkerCreateRequest(
    UUID id,
    UUID incidentId,
    UUID opId,
    String type,
    MarkerGeoJsonPoint location,
    String supportRequestType,
    String memo,
    Instant clientTs,
    Long clockOffsetMs,
    List<MarkerCreatePhotoRequest> photos) {

  public MarkerCreateRequest(
      UUID incidentId,
      UUID opId,
      String type,
      MarkerGeoJsonPoint location,
      String supportRequestType,
      String memo,
      Instant clientTs,
      Long clockOffsetMs) {
    this(
        null,
        incidentId,
        opId,
        type,
        location,
        supportRequestType,
        memo,
        clientTs,
        clockOffsetMs,
        List.of());
  }

  public MarkerCreateRequest {
    photos = photos == null ? List.of() : List.copyOf(photos);
  }
}
