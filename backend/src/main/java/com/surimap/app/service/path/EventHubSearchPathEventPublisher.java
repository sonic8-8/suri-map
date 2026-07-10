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
            request.getIncidentId(),
            request.getEventType().name(),
            PAYLOAD_FORMAT_VERSION,
            SOURCE_ENTITY_TYPE,
            request.getId(),
            occurredAt,
            payloadFor(request, occurredAt)));
  }

  private static void validate(SearchPathPublishRequest request) {
    if (request == null
        || request.getEventType() == null
        || request.getId() == null
        || request.getIncidentId() == null
        || request.getOpId() == null
        || request.getPolicePhoneId() == null
        || request.getAccountId() == null
        || request.getStatus() == null
        || request.getVersion() <= 0) {
      throw new SearchPathGuardException("write_conflict");
    }
  }

  private static UUID eventIdFor(SearchPathPublishRequest request) {
    String seed =
        "event:%s:%s:v%d"
            .formatted(request.getEventType().name(), request.getId(), request.getVersion());
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> payloadFor(
      SearchPathPublishRequest request, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", request.getId().toString());
    payload.put("incidentId", request.getIncidentId().toString());
    payload.put("opId", request.getOpId().toString());
    payload.put("policePhoneId", request.getPolicePhoneId().toString());
    payload.put("accountId", request.getAccountId().toString());
    payload.put("status", request.getStatus().name());
    payload.put("version", request.getVersion());
    payload.put("sequence", request.getVersion());
    payload.put("serverTs", occurredAt.toString());
    return payload;
  }
}
