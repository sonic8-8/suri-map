package com.surimap.marker.dto;

import java.time.Instant;
import java.util.UUID;

public interface MarkerPublishPayload {

  UUID id();

  UUID incidentId();

  UUID opId();

  UUID policePhoneId();

  String status();

  long version();

  String type();

  Instant clientTs();
}
