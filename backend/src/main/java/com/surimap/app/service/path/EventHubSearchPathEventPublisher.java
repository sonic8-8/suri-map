package com.surimap.app.service.path;

import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class EventHubSearchPathEventPublisher implements SearchPathEventPublisher {

  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final String SOURCE_ENTITY_TYPE = "search_path";

  private final EventHub eventHub;

  EventHubSearchPathEventPublisher(EventHub eventHub) {
    this.eventHub = eventHub;
  }

  @Override
  public void publish(SearchPathPublishRequest request) {
    validate(request);
    Instant occurredAt = Instant.now();
    eventHub.publish(
        new PublishRequest(
            eventIdFor(request),
            request.incidentId(),
            request.eventType().name(),
            PAYLOAD_FORMAT_VERSION,
            SOURCE_ENTITY_TYPE,
            request.id(),
            occurredAt,
            payloadFor(request, occurredAt)));
  }

  private static void validate(SearchPathPublishRequest request) {
    if (request == null
        || request.eventType() == null
        || request.id() == null
        || request.incidentId() == null
        || request.opId() == null
        || request.policePhoneId() == null
        || request.status() == null
        || request.version() <= 0) {
      throw new SearchPathGuardException("write_conflict");
    }
  }

  private static UUID eventIdFor(SearchPathPublishRequest request) {
    String seed = "event:%s:%s:v%d".formatted(request.eventType().name(), request.id(), request.version());
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> payloadFor(
      SearchPathPublishRequest request, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", request.id().toString());
    payload.put("incidentId", request.incidentId().toString());
    payload.put("opId", request.opId().toString());
    payload.put("policePhoneId", request.policePhoneId().toString());
    payload.put("status", request.status().name());
    payload.put("version", request.version());
    payload.put("sequence", request.version());
    payload.put("serverTs", occurredAt.toString());
    return payload;
  }
}
