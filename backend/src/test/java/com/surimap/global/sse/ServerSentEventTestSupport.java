package com.surimap.global.sse;

import com.surimap.global.event.EventPublishRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ServerSentEventTestSupport {

  public static final Instant CREATED_AT = Instant.parse("2026-05-08T00:00:00Z");

  private ServerSentEventTestSupport() {}

  /** HTTP·쓰기 경합 검사는 작업 선점과 분리해 실제 Worker의 전달 동작을 동기 실행한다. */
  public static ServerSentEventMessage dispatchLiveEvent(
      ServerSentEventJobService jobService,
      ServerSentEventConnectionRegistry connections,
      EventPublishRequest request,
      long sequence) {
    var worker = new ServerSentEventJobWorker(jobService, connections, false, 0, 1000, 100);
    try {
      return org.springframework.test.util.ReflectionTestUtils.invokeMethod(
          worker, "dispatchLiveEvent", request, sequence);
    } finally {
      worker.stop();
    }
  }

  public static EventPublishRequest publishRequest(UUID eventId, UUID incidentId, String type) {
    return publishRequest(
        eventId, incidentId, type, "30000000-0000-4000-8000-000000000501", "RECORDING", 7L);
  }

  public static EventPublishRequest publishRequest(
      UUID eventId, UUID incidentId, String type, String payloadId, String status, long version) {
    return EventPublishRequest.builder()
        .eventId(eventId)
        .incidentId(incidentId)
        .type(type)
        .payloadFormatVersion(1)
        .sourceEntityType("search_path")
        .sourceEntityId(UUID.fromString(payloadId))
        .occurredAt(CREATED_AT)
        .payload(Map.of("id", payloadId, "status", status, "version", version))
        .build();
  }
}
