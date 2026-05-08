package com.surimap.eventhub;

import com.surimap.eventhub.dto.PublishRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class EventStreamTestFixtures {

  static final Instant CREATED_AT = Instant.parse("2026-05-08T00:00:00Z");

  private EventStreamTestFixtures() {}

  static PublishRequest publishRequest(UUID eventId, UUID incidentId, String type) {
    return publishRequest(
        eventId, incidentId, type, "30000000-0000-4000-8000-000000000501", "RECORDING", 7L);
  }

  static PublishRequest publishRequest(
      UUID eventId, UUID incidentId, String type, String payloadId, String status, long version) {
    return new PublishRequest(
        eventId,
        incidentId,
        type,
        1,
        "search_path",
        UUID.fromString(payloadId),
        CREATED_AT,
        Map.of("id", payloadId, "status", status, "version", version));
  }
}
