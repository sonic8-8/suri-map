package com.surimap.policephone;

import com.surimap.eventhub.dto.PublishRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PublishRequest source contract for POLICE_PHONE_HEARTBEAT_UPDATED.
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

  public PublishRequest toPublishRequest(UUID incidentId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", id);
    payload.put("status", status.name());
    payload.put("version", version);
    payload.put("policePhoneId", policePhoneId);
    payload.put("sequence", sequence);
    payload.put("lastHeartbeatAt", lastHeartbeatAt);
    payload.put("lastSyncAt", lastSyncAt);

    return new PublishRequest(
        id,
        incidentId,
        TYPE,
        PAYLOAD_FORMAT_VERSION,
        "police_phone",
        policePhoneId,
        lastHeartbeatAt,
        payload);
  }
}
