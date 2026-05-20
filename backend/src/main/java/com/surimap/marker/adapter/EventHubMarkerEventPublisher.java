package com.surimap.marker.adapter;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishPayload;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.event.MarkerEventIds;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.port.MarkerEventPublisher;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Publishes S5 marker events into the shared S4 event outbox. */
@Component
public class EventHubMarkerEventPublisher implements MarkerEventPublisher {

  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final String MARKER_SOURCE_ENTITY_TYPE = "marker";
  private static final String MARKER_NOTIFICATION_SOURCE_ENTITY_TYPE = "marker_notification";

  private final EventHub eventHub;

  public EventHubMarkerEventPublisher(EventHub eventHub) {
    this.eventHub = eventHub;
  }

  @Override
  public void publish(MarkerPublishRequest request) {
    if (request == null || request.type() == null || request.payload() == null) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
    MarkerPublishPayload payload = request.payload();
    if (payload.id() == null || payload.incidentId() == null || payload.version() <= 0) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }

    eventHub.publish(
        new PublishRequest(
            eventIdFor(request),
            payload.incidentId(),
            request.type(),
            PAYLOAD_FORMAT_VERSION,
            sourceEntityType(request),
            payload.id(),
            occurredAt(payload),
            payloadFor(payload)));
  }

  private static UUID eventIdFor(MarkerPublishRequest request) {
    MarkerPublishPayload payload = request.payload();
    return MarkerEventIds.eventId(request.type(), payload.id(), payload.version());
  }

  private static String sourceEntityType(MarkerPublishRequest request) {
    if (request.type().startsWith("MARKER_")) {
      return MARKER_SOURCE_ENTITY_TYPE;
    }
    return MARKER_NOTIFICATION_SOURCE_ENTITY_TYPE;
  }

  private static Instant occurredAt(MarkerPublishPayload payload) {
    return payload.serverTs() == null ? Instant.now() : payload.serverTs();
  }

  private static Map<String, Object> payloadFor(MarkerPublishPayload payload) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", payload.id().toString());
    values.put("incidentId", payload.incidentId().toString());
    values.put("opId", payload.opId() == null ? null : payload.opId().toString());
    values.put(
        "policePhoneId",
        payload.policePhoneId() == null ? null : payload.policePhoneId().toString());
    values.put("status", payload.status());
    values.put("version", payload.version());
    putIfPresent(values, "type", payload.type());
    putIfPresent(values, "location", locationPayload(payload.location()));
    putIfPresent(values, "clientTs", payload.clientTs());
    putIfPresent(values, "serverTs", payload.serverTs());
    putIfPresent(values, "markerId", payload.markerId());
    putIfPresent(values, "recipientPolicy", payload.recipientPolicy());
    if (!payload.recipientAccountIds().isEmpty()) {
      values.put("recipientAccountIds", payload.recipientAccountIds());
    }
    if (!payload.recipientPolicePhoneIds().isEmpty()) {
      values.put("recipientPolicePhoneIds", payload.recipientPolicePhoneIds());
    }
    putIfPresent(values, "markerType", payload.markerType());
    putIfPresent(values, "locationLabel", payload.locationLabel());
    putIfPresent(values, "policePhoneName", payload.policePhoneName());
    return values;
  }

  private static Map<String, Object> locationPayload(MarkerGeoJsonPoint location) {
    if (location == null) {
      return null;
    }
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("type", location.type());
    values.put("coordinates", location.coordinates());
    return values;
  }

  private static void putIfPresent(Map<String, Object> values, String key, Object value) {
    if (value != null) {
      if (value instanceof UUID uuid) {
        values.put(key, uuid.toString());
        return;
      }
      if (value instanceof Instant instant) {
        values.put(key, instant.toString());
        return;
      }
      values.put(key, value);
    }
  }
}
