package com.surimap.marker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MarkerPublishPayload {

  UUID id();

  UUID incidentId();

  UUID opId();

  UUID policePhoneId();

  String status();

  long version();

  default String type() {
    return null;
  }

  default MarkerGeoJsonPoint location() {
    return null;
  }

  default Instant clientTs() {
    return null;
  }

  default Instant serverTs() {
    return null;
  }

  default UUID markerId() {
    return null;
  }

  default String recipientPolicy() {
    return null;
  }

  default List<String> recipientAccountIds() {
    return List.of();
  }

  default List<String> recipientPolicePhoneIds() {
    return List.of();
  }

  default String markerType() {
    return null;
  }

  default String locationLabel() {
    return null;
  }
}
