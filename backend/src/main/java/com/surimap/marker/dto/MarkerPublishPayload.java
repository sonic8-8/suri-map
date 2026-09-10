package com.surimap.marker.dto;

import java.time.Instant;
import java.util.UUID;

public interface MarkerPublishPayload {

  UUID getId();

  UUID getIncidentId();

  UUID getOpId();

  UUID getPolicePhoneId();

  String getStatus();

  long getVersion();

  String getType();

  Instant getClientTs();
}
