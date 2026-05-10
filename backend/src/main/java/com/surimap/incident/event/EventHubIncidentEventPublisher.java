package com.surimap.incident.event;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** L1 incident event publish 요청을 S4 EventHub outbox로 넘기는 adapter. */
@Component
public class EventHubIncidentEventPublisher implements IncidentEventPublisher {

  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final String SOURCE_ENTITY_TYPE = "incident";

  private final EventHub eventHub;

  public EventHubIncidentEventPublisher(EventHub eventHub) {
    this.eventHub = eventHub;
  }

  @Override
  public void publishIncidentCreated(IncidentCreatedEvent event) {
    Map<String, Object> payload = basePayload(event.id(), event.status(), event.version());
    payload.put("sourceIncidentId", event.sourceIncidentId());
    payload.put("memberAccountIds", event.memberAccountIds());
    publish("INCIDENT_CREATED", event.id(), event.version(), payload);
  }

  @Override
  public void publishIncidentAssignmentChanged(IncidentAssignmentChangedEvent event) {
    Map<String, Object> payload = basePayload(event.id(), event.status(), event.version());
    payload.put("changedAccountIds", event.changedAccountIds());
    publish("INCIDENT_ASSIGNMENT_CHANGED", event.id(), event.version(), payload);
  }

  @Override
  public void publishIncidentClosed(IncidentClosedEvent event) {
    Map<String, Object> payload = basePayload(event.id(), event.status(), event.version());
    payload.put("closedAt", event.closedAt());
    payload.put("writeDisabledReason", event.writeDisabledReason());
    publish("INCIDENT_CLOSED", event.id(), event.version(), payload);
  }

  private void publish(String type, UUID incidentId, long version, Map<String, Object> payload) {
    eventHub.publish(
        new PublishRequest(
            eventIdFor(type, incidentId, version),
            incidentId,
            type,
            PAYLOAD_FORMAT_VERSION,
            SOURCE_ENTITY_TYPE,
            incidentId,
            Instant.now(),
            payload));
  }

  private static Map<String, Object> basePayload(UUID id, String status, long version) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", id.toString());
    payload.put("status", status);
    payload.put("version", version);
    return payload;
  }

  private static UUID eventIdFor(String type, UUID incidentId, long version) {
    String seed = "event:" + type + ":" + incidentId + ":v" + version;
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }
}
