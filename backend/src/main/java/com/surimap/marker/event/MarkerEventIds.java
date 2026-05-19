package com.surimap.marker.event;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class MarkerEventIds {

  private MarkerEventIds() {}

  public static UUID eventId(String eventType, UUID sourceId, long version) {
    Objects.requireNonNull(eventType, "eventType must not be null");
    Objects.requireNonNull(sourceId, "sourceId must not be null");
    return UUID.nameUUIDFromBytes(
        ("event:" + eventType + ":" + sourceId + ":v" + version)
            .getBytes(StandardCharsets.UTF_8));
  }
}
