package com.surimap.operationalperiod.event;

import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.event.EventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** S8 OP_TRANSITIONED publish 요청을 S4 EventPublisher outbox로 넘기는 adapter. */
@Component
public class EventHubOperationalPeriodEventPublisher implements EventPublisherPort {

  private static final int PAYLOAD_FORMAT_VERSION = 1;

  private final EventPublisher eventHub;

  public EventHubOperationalPeriodEventPublisher(EventPublisher eventHub) {
    this.eventHub = eventHub;
  }

  @Override
  public void publish(OpTransitionedPublishRequest request) {
    eventHub.publish(
        EventPublishRequest.builder()
            .eventId(eventIdFor(request))
            .incidentId(request.incidentId())
            .type(request.type())
            .payloadFormatVersion(PAYLOAD_FORMAT_VERSION)
            .sourceEntityType("operational_period")
            .sourceEntityId(request.opId())
            .occurredAt(Instant.now())
            .payload(payloadFor(request))
            .build());
  }

  private static UUID eventIdFor(OpTransitionedPublishRequest request) {
    String seed =
        "event:"
            + request.type()
            + ":"
            + request.id()
            + ":v"
            + request.version()
            + ":seq"
            + request.sequenceNumber();
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> payloadFor(OpTransitionedPublishRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", request.id().toString());
    payload.put("incidentId", request.incidentId().toString());
    payload.put("opId", request.opId().toString());
    payload.put("status", request.status());
    payload.put("version", request.version());
    payload.put("sequenceNumber", request.sequenceNumber());
    payload.put("fromOpId", request.fromOpId() == null ? null : request.fromOpId().toString());
    payload.put("toOpId", request.toOpId() == null ? null : request.toOpId().toString());
    return payload;
  }
}
