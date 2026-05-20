package com.surimap.path;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Primary
public class EventHubPathEventPublisher implements PathEventPublisher {

  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final String PATH_APPENDED = "PATH_APPENDED";
  private static final String SEARCH_PATH_SEGMENT_UPDATED = "SEARCH_PATH_SEGMENT_UPDATED";

  private final EventHub eventHub;
  private final Environment environment;

  public EventHubPathEventPublisher(EventHub eventHub, Environment environment) {
    this.eventHub = eventHub;
    this.environment = environment;
  }

  @Override
  public void publishPathAppended(PathAppendedPublishRequest request) {
    validate(request);
    if (!postgresqlDataSource(environment)) {
      return;
    }
    Instant occurredAt = Instant.now();
    eventHub.publish(
        new PublishRequest(
            eventIdFor(PATH_APPENDED, request.id(), request.version()),
            request.incidentId(),
            PATH_APPENDED,
            PAYLOAD_FORMAT_VERSION,
            "search_path",
            request.id(),
            occurredAt,
            pathPayload(request, occurredAt)));
  }

  @Override
  public void publishSegmentUpdated(SearchPathSegmentUpdatedPublishRequest request) {
    validate(request);
    if (!postgresqlDataSource(environment)) {
      return;
    }
    Instant occurredAt = Instant.now();
    UUID segmentId = sourceSegmentId(request.segmentId());
    eventHub.publish(
        new PublishRequest(
            eventIdFor(SEARCH_PATH_SEGMENT_UPDATED, segmentId, request.version()),
            request.incidentId(),
            SEARCH_PATH_SEGMENT_UPDATED,
            PAYLOAD_FORMAT_VERSION,
            "search_path_segment",
            segmentId,
            occurredAt,
            segmentPayload(request, occurredAt)));
  }

  private static void validate(PathAppendedPublishRequest request) {
    if (request == null
        || request.id() == null
        || request.incidentId() == null
        || request.status() == null
        || request.version() <= 0
        || request.opId() == null
        || request.policePhoneId() == null
        || request.accountId() == null) {
      throw new SearchPathApiException("write_conflict");
    }
  }

  private static void validate(SearchPathSegmentUpdatedPublishRequest request) {
    if (request == null
        || request.id() == null
        || request.incidentId() == null
        || request.status() == null
        || request.version() <= 0
        || request.opId() == null
        || request.policePhoneId() == null
        || request.accountId() == null
        || request.segmentId() == null
        || request.movementType() == null
        || request.movementTypeSource() == null) {
      throw new SearchPathApiException("write_conflict");
    }
  }

  private static UUID eventIdFor(String type, UUID sourceId, long version) {
    String seed = "event:%s:%s:v%d".formatted(type, sourceId, version);
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private static UUID sourceSegmentId(String segmentId) {
    try {
      return UUID.fromString(segmentId);
    } catch (IllegalArgumentException ignored) {
      return UUID.nameUUIDFromBytes(("search-path-segment:" + segmentId).getBytes(StandardCharsets.UTF_8));
    }
  }

  private static Map<String, Object> pathPayload(
      PathAppendedPublishRequest request, Instant occurredAt) {
    Map<String, Object> payload = basePayload(
        request.id(),
        request.incidentId(),
        request.opId(),
        request.policePhoneId(),
        request.accountId(),
        request.status(),
        request.version(),
        occurredAt);
    return payload;
  }

  private static Map<String, Object> segmentPayload(
      SearchPathSegmentUpdatedPublishRequest request, Instant occurredAt) {
    Map<String, Object> payload = basePayload(
        request.id(),
        request.incidentId(),
        request.opId(),
        request.policePhoneId(),
        request.accountId(),
        request.status(),
        request.version(),
        occurredAt);
    payload.put("segmentId", request.segmentId());
    payload.put("movementType", request.movementType().name());
    payload.put("movementTypeSource", request.movementTypeSource().name());
    return payload;
  }

  private static Map<String, Object> basePayload(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID policePhoneId,
      UUID accountId,
      SearchPathStatus status,
      long version,
      Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", id.toString());
    payload.put("incidentId", incidentId.toString());
    payload.put("opId", opId.toString());
    payload.put("policePhoneId", policePhoneId.toString());
    payload.put("accountId", accountId.toString());
    payload.put("status", status.name());
    payload.put("version", version);
    payload.put("sequence", version);
    payload.put("serverTs", occurredAt.toString());
    return payload;
  }

  private static boolean postgresqlDataSource(Environment environment) {
    String driver = environment.getProperty("spring.datasource.driver-class-name", "");
    String url = environment.getProperty("spring.datasource.url", "");
    return driver.contains("postgresql") || url.startsWith("jdbc:postgresql:");
  }
}
