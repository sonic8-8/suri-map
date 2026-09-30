package com.surimap.policephone;

import com.surimap.global.event.EventPublishRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * EventPublishRequest source contract for POLICE_PHONE_HEARTBEAT_UPDATED.
 *
 * <p>The payload includes id, status, version, policePhoneId, sequence, lastHeartbeatAt, and
 * lastSyncAt. Heartbeat success always reports ONLINE.
 */
public record PolicePhoneHeartbeatUpdatedPublishRequest(
    UUID id,
    PolicePhoneFreshnessStatus status,
    long version,
    UUID policePhoneId,
    long sequence,
    Instant lastHeartbeatAt,
    Instant lastSyncAt) {

  public static final String TYPE = "POLICE_PHONE_HEARTBEAT_UPDATED";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  public static PolicePhoneHeartbeatUpdatedPublishRequest from(PolicePhoneHeartbeatResult result) {
    return new PolicePhoneHeartbeatUpdatedPublishRequest(
        result.id(),
        result.status(),
        result.version(),
        result.policePhoneId(),
        result.sequence(),
        result.lastHeartbeatAt(),
        result.lastSyncAt());
  }

  public EventPublishRequest toPublishRequest(UUID incidentId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", id);
    payload.put("status", status.name());
    payload.put("version", version);
    payload.put("policePhoneId", policePhoneId);
    payload.put("sequence", sequence);
    payload.put("lastHeartbeatAt", lastHeartbeatAt);
    payload.put("lastSyncAt", lastSyncAt);

    return EventPublishRequest.builder()
        .eventId(id)
        .incidentId(incidentId)
        .type(TYPE)
        .payloadFormatVersion(PAYLOAD_FORMAT_VERSION)
        .sourceEntityType("police_phone")
        .sourceEntityId(policePhoneId)
        .occurredAt(lastHeartbeatAt)
        .payload(payload)
        .build();
  }
}
