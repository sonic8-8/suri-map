package com.surimap.marker.adapter;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.marker.dto.MarkerEventPayload;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerNotificationPayload;
import com.surimap.marker.dto.MarkerPublishPayload;
import com.surimap.marker.event.MarkerEventIds;
import com.surimap.marker.exception.MarkerApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Publishes S5 marker events into the shared S4 event outbox. */
@Component
public class MarkerEventPublisher {

  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final String MARKER_SOURCE_ENTITY_TYPE = "marker";
  private static final String MARKER_NOTIFICATION_SOURCE_ENTITY_TYPE = "marker_notification";

  private final EventHub eventHub;

  public MarkerEventPublisher(EventHub eventHub) {
    this.eventHub = eventHub;
  }

  public void publish(String eventType, MarkerPublishPayload payload) {
    if (eventType == null || payload == null) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
    if (payload.getId() == null || payload.getIncidentId() == null || payload.getVersion() <= 0) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }

    eventHub.publish(
        new PublishRequest(
            MarkerEventIds.eventId(eventType, payload.getId(), payload.getVersion()),
            payload.getIncidentId(),
            eventType,
            PAYLOAD_FORMAT_VERSION,
            sourceEntityType(eventType),
            payload.getId(),
            occurredAt(payload),
            payloadFor(payload)));
  }

  private static String sourceEntityType(String eventType) {
    if (eventType.startsWith("MARKER_")) {
      return MARKER_SOURCE_ENTITY_TYPE;
    }
    return MARKER_NOTIFICATION_SOURCE_ENTITY_TYPE;
  }

  private static Instant occurredAt(MarkerPublishPayload payload) {
    if (payload instanceof MarkerEventPayload markerPayload
        && markerPayload.getServerTs() != null) {
      return markerPayload.getServerTs();
    }
    return Instant.now();
  }

  private static Map<String, Object> payloadFor(MarkerPublishPayload payload) {
    if (payload instanceof MarkerNotificationPayload notificationPayload) {
      Map<String, Object> values = notificationPayload.toMap();
      // 알림 이벤트에서는 선택 필드의 null과 빈 수신자 목록을 생략한다.
      for (String field :
          List.of(
              "type",
              "markerId",
              "recipientPolicy",
              "markerType",
              "locationLabel",
              "policePhoneName")) {
        values.remove(field, null);
      }
      if (notificationPayload.getRecipientAccountIds().isEmpty()) {
        values.remove("recipientAccountIds");
      }
      if (notificationPayload.getRecipientPolicePhoneIds().isEmpty()) {
        values.remove("recipientPolicePhoneIds");
      }
      return values;
    }

    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", payload.getId().toString());
    values.put("incidentId", payload.getIncidentId().toString());
    values.put("opId", payload.getOpId() == null ? null : payload.getOpId().toString());
    values.put(
        "policePhoneId",
        payload.getPolicePhoneId() == null ? null : payload.getPolicePhoneId().toString());
    values.put("status", payload.getStatus());
    values.put("version", payload.getVersion());
    putIfPresent(values, "type", payload.getType());
    putIfPresent(values, "clientTs", payload.getClientTs());
    if (payload instanceof MarkerEventPayload markerPayload) {
      putIfPresent(values, "location", locationPayload(markerPayload.getLocation()));
      putIfPresent(values, "serverTs", markerPayload.getServerTs());
    }
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
