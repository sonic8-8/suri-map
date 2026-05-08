package com.surimap.retention.purge;

import com.surimap.eventhub.dto.PublishRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record IncidentPurgedPublishRequest(
    UUID incidentId, long version, UUID purgeRunId, Instant purgedAt) {

  public static final String TYPE = "INCIDENT_PURGED";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  public IncidentPurgedPublishRequest {
    Objects.requireNonNull(incidentId, "incidentId는 null일 수 없습니다");
    Objects.requireNonNull(purgeRunId, "purgeRunId는 null일 수 없습니다");
    Objects.requireNonNull(purgedAt, "purgedAt는 null일 수 없습니다");
  }

  public PublishRequest toPublishRequest() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", incidentId.toString());
    payload.put("status", "PURGED");
    payload.put("version", version);
    payload.put("purgeRunId", purgeRunId.toString());
    payload.put("purgedAt", purgedAt.toString());

    return new PublishRequest(
        eventId(),
        incidentId,
        TYPE,
        PAYLOAD_FORMAT_VERSION,
        "incident_data_purge",
        purgeRunId,
        purgedAt,
        payload);
  }

  private UUID eventId() {
    String source = TYPE + ":" + incidentId + ":" + purgeRunId + ":" + version;
    return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
  }
}
