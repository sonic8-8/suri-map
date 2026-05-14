package com.surimap.marker.photo.adapter;

import com.surimap.eventhub.port.EventHub;
import com.surimap.marker.photo.dto.PhotoDelta;
import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.dto.PublishRequestPayload;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Publishes S5 photo attach marker updates into the shared S4 event outbox. */
@Component
public class EventHubPhotoEventPublisher implements PhotoEventPublisher {

  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final String SOURCE_ENTITY_TYPE = "marker";

  private final EventHub eventHub;

  public EventHubPhotoEventPublisher(EventHub eventHub) {
    this.eventHub = eventHub;
  }

  @Override
  public void publish(PublishRequest request) {
    if (request == null || request.type() == null || request.payload() == null) {
      throw conflict();
    }
    PublishRequestPayload payload = request.payload();
    if (payload.id() == null
        || payload.incidentId() == null
        || payload.version() <= 0
        || payload.photoDelta() == null) {
      throw conflict();
    }

    eventHub.publish(
        new com.surimap.eventhub.dto.PublishRequest(
            eventIdFor(request),
            payload.incidentId(),
            request.type(),
            PAYLOAD_FORMAT_VERSION,
            SOURCE_ENTITY_TYPE,
            payload.id(),
            Instant.now(),
            payloadFor(payload)));
  }

  private static UUID eventIdFor(PublishRequest request) {
    PublishRequestPayload payload = request.payload();
    return UUID.nameUUIDFromBytes(
        ("event:" + request.type() + ":" + payload.id() + ":v" + payload.version())
            .getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> payloadFor(PublishRequestPayload payload) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", payload.id().toString());
    values.put("incidentId", payload.incidentId().toString());
    values.put("opId", payload.opId() == null ? null : payload.opId().toString());
    values.put(
        "policePhoneId",
        payload.policePhoneId() == null ? null : payload.policePhoneId().toString());
    values.put("status", payload.status());
    values.put("version", payload.version());
    values.put("photoDelta", photoDeltaPayload(payload.photoDelta()));
    return values;
  }

  private static Map<String, Object> photoDeltaPayload(PhotoDelta photoDelta) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("photoId", photoDelta.photoId().toString());
    values.put("status", photoDelta.status());
    values.put("version", photoDelta.version());
    return values;
  }

  private static PhotoApiException conflict() {
    return new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
  }
}
