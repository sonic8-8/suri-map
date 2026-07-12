package com.surimap.global.event;

import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathApiException;
import com.surimap.domain.path.SearchPathEventType;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SearchPathEventPublisher {

  private static final int PAYLOAD_FORMAT_VERSION = 1;
  private static final String PATH_APPENDED = "PATH_APPENDED";
  private static final String SEARCH_PATH_SEGMENT_UPDATED = "SEARCH_PATH_SEGMENT_UPDATED";

  private final EventHub eventHub;

  public SearchPathEventPublisher(EventHub eventHub) {
    this.eventHub = eventHub;
  }

  public void publishLifecycle(SearchPath path, SearchPathEventType eventType) {
    validateLifecycle(path, eventType);
    publishPathEvent(eventType.name(), path);
  }

  public void publishPathAppended(SearchPath path) {
    validatePath(path);
    publishPathEvent(PATH_APPENDED, path);
  }

  public void publishSegmentUpdated(SearchPath path, SearchPathSegment segment) {
    validateSegment(path, segment);
    Instant occurredAt = Instant.now();
    eventHub.publish(
        new PublishRequest(
            eventIdFor(SEARCH_PATH_SEGMENT_UPDATED, segment.getId(), path.getVersion()),
            path.getIncidentId(),
            SEARCH_PATH_SEGMENT_UPDATED,
            PAYLOAD_FORMAT_VERSION,
            "search_path_segment",
            segment.getId(),
            occurredAt,
            segmentPayload(path, segment, occurredAt)));
  }

  private void publishPathEvent(String eventType, SearchPath path) {
    Instant occurredAt = Instant.now();
    eventHub.publish(
        new PublishRequest(
            eventIdFor(eventType, path.getId(), path.getVersion()),
            path.getIncidentId(),
            eventType,
            PAYLOAD_FORMAT_VERSION,
            "search_path",
            path.getId(),
            occurredAt,
            basePayload(path, occurredAt)));
  }

  private static void validateLifecycle(SearchPath path, SearchPathEventType eventType) {
    if (eventType == null
        || path == null
        || path.getId() == null
        || path.getIncidentId() == null
        || path.getOpId() == null) {
      throw new SearchPathGuardException("write_conflict");
    }
    if (path.getAccountId() == null
        || path.getStatus() == null
        || path.getVersion() <= 0) {
      throw new SearchPathGuardException("write_conflict");
    }
  }

  private static void validatePath(SearchPath path) {
    if (invalidPath(path)) {
      throw new SearchPathApiException("write_conflict");
    }
  }

  private static void validateSegment(SearchPath path, SearchPathSegment segment) {
    if (invalidPath(path)
        || segment == null
        || segment.getId() == null
        || segment.getMovementType() == null
        || segment.getMovementTypeSource() == null) {
      throw new SearchPathApiException("write_conflict");
    }
  }

  private static boolean invalidPath(SearchPath path) {
    return path == null
        || path.getId() == null
        || path.getIncidentId() == null
        || path.getStatus() == null
        || path.getVersion() <= 0
        || path.getOpId() == null
        || path.getAccountId() == null;
  }

  private static UUID eventIdFor(String type, UUID sourceId, long version) {
    String seed = "event:%s:%s:v%d".formatted(type, sourceId, version);
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> segmentPayload(
      SearchPath path, SearchPathSegment segment, Instant occurredAt) {
    Map<String, Object> payload = basePayload(path, occurredAt);
    payload.put("segmentId", segment.getId().toString());
    payload.put("movementType", segment.getMovementType().name());
    payload.put("movementTypeSource", segment.getMovementTypeSource().name());
    return payload;
  }

  private static Map<String, Object> basePayload(
      SearchPath path, Instant occurredAt) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", path.getId().toString());
    payload.put("incidentId", path.getIncidentId().toString());
    payload.put("opId", path.getOpId().toString());
    payload.put("accountId", path.getAccountId().toString());
    payload.put("status", path.getStatus().name());
    payload.put("version", path.getVersion());
    payload.put("sequence", path.getVersion());
    payload.put("serverTs", occurredAt.toString());
    return payload;
  }
}
