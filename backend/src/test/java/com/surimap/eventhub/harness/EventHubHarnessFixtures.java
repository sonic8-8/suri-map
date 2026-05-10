package com.surimap.eventhub.harness;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.fixture.EventFixtures;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * L2-T09B S4 Realtime Event Hub 하네스 픽스처.
 *
 * <p>S4.json harness_fixtures 섹션에 정의된 시나리오별 요청 픽스처를 제공한다.
 * fixture ID는 {@link EventFixtures} 상수를 그대로 사용하며, 임의로 축약하거나 재명명하지 않는다.
 */
public final class EventHubHarnessFixtures {

  private EventHubHarnessFixtures() {}

  /** SC-08: SUPPORT_REQUEST_CREATED 픽스처 요청 */
  public static HarnessRequest sc08SupportRequest() {
    return new HarnessRequest(
        EventFixtures.SC08_EVENT_ID,
        EventFixtures.INCIDENT_ID_01,
        "SUPPORT_REQUEST_CREATED",
        1,
        "marker_notification",
        UUID.fromString("50000000-0000-4000-8000-000000000801"),
        Instant.parse("2026-05-01T00:00:00Z"),
        Map.of(
            "id", "50000000-0000-4000-8000-000000000801",
            "status", "REQUESTED",
            "version", 1));
  }

  /** SC-09: PATH_APPENDED 픽스처 요청 */
  public static HarnessRequest sc09PathAppended() {
    return new HarnessRequest(
        EventFixtures.SC09_EVENT_ID,
        EventFixtures.INCIDENT_ID_01,
        "PATH_APPENDED",
        1,
        "search_path",
        UUID.fromString("30000000-0000-4000-8000-000000000501"),
        Instant.parse("2026-05-01T00:01:00Z"),
        Map.of(
            "id", "30000000-0000-4000-8000-000000000501",
            "status", "RECORDING",
            "version", 7));
  }

  /** DEDUPE: 중복 이벤트 픽스처 요청 */
  public static HarnessRequest dedupeEventFirst() {
    return new HarnessRequest(
        EventFixtures.DEDUPE_EVENT_ID,
        EventFixtures.INCIDENT_ID_01,
        "SUPPORT_REQUEST_CREATED",
        1,
        "marker_notification",
        UUID.fromString("50000000-0000-4000-8000-000000000701"),
        Instant.parse("2026-05-01T00:02:00Z"),
        Map.of(
            "id", "50000000-0000-4000-8000-000000000701",
            "status", "REQUESTED",
            "version", 1));
  }

  /**
   * 이벤트 발행 하네스 요청.
   *
   * <p>BaseEvent envelope의 모든 필드를 포함하며, {@link #toPublishRequest()} 로 변환한다.
   */
  public record HarnessRequest(
      UUID eventId,
      UUID incidentId,
      String type,
      int payloadFormatVersion,
      String sourceEntityType,
      UUID sourceEntityId,
      Instant occurredAt,
      Map<String, Object> payload) {

    public PublishRequest toPublishRequest() {
      return new PublishRequest(
          eventId, incidentId, type, payloadFormatVersion, sourceEntityType, sourceEntityId,
          occurredAt, payload);
    }
  }

  /** publish() 결과 증거 레코드 */
  public record PublishEvidence(
      UUID eventId,
      UUID incidentId,
      String type,
      Map<String, Object> payload) {}

  /** SSE replay store 기록 증거 레코드 */
  public record SseReplayEvidence(
      UUID eventId,
      UUID incidentId,
      long replaySequence) {}
}
