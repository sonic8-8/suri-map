package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.fixture.EventFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MockEventHub publish hook 계약 테스트 (L2-B03A).
 *
 * <p>real event_dispatch_job 또는 SSE dispatch 구현 전에 MockEventHub를 사용해 이벤트 발행 기록과 실패 주입을 검증한다.
 *
 * <p>Spring 컨텍스트 없이 순수 unit test로 실행된다.
 *
 * <p>참조:
 *
 * <ul>
 *   <li>docs/spec/specs/S4.json §harness_fixtures
 *   <li>docs/spec/boundaries.md §9.1 BaseEvent envelope
 *   <li>docs/spec/boundaries.md §4.4 Event Catalog
 * </ul>
 */
@DisplayName("MockEventHub publish hook 계약")
class MockPublishHookContractTest {

  private MockEventHub hub;

  @BeforeEach
  void setUp() {
    hub = new MockEventHub();
  }

  // ═══════════════════════════════════════════════════════════
  //  1. 기본 publish 캡처
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("기본 publish 캡처")
  class PublishCapture {

    @Test
    @DisplayName("publishCapturesRequest — publish 후 eventId로 조회 시 모든 필드가 일치한다")
    void publishCapturesRequest() {
      UUID eventId = EventFixtures.SC08_EVENT_ID;
      UUID incidentId = EventFixtures.INCIDENT_ID_01;
      String type = EventFixtures.SC08_EVENT_TYPE;
      int payloadFormatVersion = 1;
      String sourceEntityType = "marker_notification";
      UUID sourceEntityId = UUID.fromString("50000000-0000-4000-8000-000000000801");
      Instant occurredAt = Instant.parse("2026-05-07T00:00:00Z");
      Map<String, Object> payload =
          Map.of(
              "id", "50000000-0000-4000-8000-000000000801",
              "status", "REQUESTED",
              "version", 1);

      PublishRequest request =
          new PublishRequest(
              eventId,
              incidentId,
              type,
              payloadFormatVersion,
              sourceEntityType,
              sourceEntityId,
              occurredAt,
              payload);

      hub.publish(request);

      Optional<MockEventHub.CapturedPublish> found = hub.findByEventId(eventId);
      assertThat(found).isPresent();

      MockEventHub.CapturedPublish captured = found.get();
      assertThat(captured.eventId()).isEqualTo(eventId);
      assertThat(captured.incidentId()).isEqualTo(incidentId);
      assertThat(captured.type()).isEqualTo(type);
      assertThat(captured.payloadFormatVersion()).isEqualTo(1);
      assertThat(captured.payload())
          .containsEntry("id", "50000000-0000-4000-8000-000000000801")
          .containsEntry("status", "REQUESTED")
          .containsEntry("version", 1);
    }

    @Test
    @DisplayName("publishCapturesMultipleRequests — 두 번 publish 후 getPublishCount() == 2")
    void publishCapturesMultipleRequests() {
      PublishRequest sc08 =
          new PublishRequest(
              EventFixtures.SC08_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC08_EVENT_TYPE,
              1,
              "marker_notification",
              UUID.fromString("50000000-0000-4000-8000-000000000801"),
              Instant.now(),
              Map.of(
                  "id", "50000000-0000-4000-8000-000000000801", "status", "REQUESTED", "version",
                  1));

      PublishRequest sc09 =
          new PublishRequest(
              EventFixtures.SC09_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC09_EVENT_TYPE,
              1,
              "search_path",
              UUID.fromString("30000000-0000-4000-8000-000000000501"),
              Instant.now(),
              Map.of(
                  "id", "30000000-0000-4000-8000-000000000501", "status", "RECORDING", "version",
                  7));

      hub.publish(sc08);
      hub.publish(sc09);

      assertThat(hub.getPublishCount()).isEqualTo(2);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  2. 타입/사건 기반 조회
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("타입·사건 기반 조회")
  class QueryByTypeAndIncident {

    @Test
    @DisplayName("findByTypeReturnsMatchingRequests — SC08_EVENT_TYPE으로 findByType() 조회")
    void findByTypeReturnsMatchingRequests() {
      PublishRequest sc08 =
          new PublishRequest(
              EventFixtures.SC08_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC08_EVENT_TYPE,
              1,
              "marker_notification",
              UUID.fromString("50000000-0000-4000-8000-000000000801"),
              Instant.now(),
              Map.of(
                  "id", "50000000-0000-4000-8000-000000000801", "status", "REQUESTED", "version",
                  1));
      hub.publish(sc08);

      List<MockEventHub.CapturedPublish> results = hub.findByType(EventFixtures.SC08_EVENT_TYPE);

      assertThat(results).hasSize(1);
      assertThat(results.get(0).type()).isEqualTo(EventFixtures.SC08_EVENT_TYPE);
      assertThat(results.get(0).eventId()).isEqualTo(EventFixtures.SC08_EVENT_ID);
    }

    @Test
    @DisplayName("findByIncidentIdReturnsMatchingRequests — INCIDENT_ID_01로 findByIncidentId() 조회")
    void findByIncidentIdReturnsMatchingRequests() {
      PublishRequest sc08 =
          new PublishRequest(
              EventFixtures.SC08_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC08_EVENT_TYPE,
              1,
              "marker_notification",
              UUID.fromString("50000000-0000-4000-8000-000000000801"),
              Instant.now(),
              Map.of(
                  "id", "50000000-0000-4000-8000-000000000801", "status", "REQUESTED", "version",
                  1));
      PublishRequest sc09 =
          new PublishRequest(
              EventFixtures.SC09_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC09_EVENT_TYPE,
              1,
              "search_path",
              UUID.fromString("30000000-0000-4000-8000-000000000501"),
              Instant.now(),
              Map.of(
                  "id", "30000000-0000-4000-8000-000000000501", "status", "RECORDING", "version",
                  7));
      hub.publish(sc08);
      hub.publish(sc09);

      List<MockEventHub.CapturedPublish> results =
          hub.findByIncidentId(EventFixtures.INCIDENT_ID_01);

      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(MockEventHub.CapturedPublish::incidentId)
          .containsOnly(EventFixtures.INCIDENT_ID_01);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  3. Failure injection
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Failure injection")
  class FailureInjection {

    @Test
    @DisplayName("failureInjectionBlocksPublish — injectFailureFor 후 publish 시 RuntimeException")
    void failureInjectionBlocksPublish() {
      UUID eventId = EventFixtures.SC08_EVENT_ID;
      hub.injectFailureFor(eventId);

      PublishRequest request =
          new PublishRequest(
              eventId,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC08_EVENT_TYPE,
              1,
              "marker_notification",
              UUID.fromString("50000000-0000-4000-8000-000000000801"),
              Instant.now(),
              Map.of(
                  "id", "50000000-0000-4000-8000-000000000801", "status", "REQUESTED", "version",
                  1));

      assertThatThrownBy(() -> hub.publish(request)).isInstanceOf(RuntimeException.class);

      assertThat(hub.hasNoPublishFor(eventId)).isTrue();
    }

    @Test
    @DisplayName("clearFailureInjectionAllowsPublish — clearFailureInjection 후 정상 publish")
    void clearFailureInjectionAllowsPublish() {
      UUID eventId = EventFixtures.SC08_EVENT_ID;
      hub.injectFailureFor(eventId);
      hub.clearFailureInjection(eventId);

      PublishRequest request =
          new PublishRequest(
              eventId,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC08_EVENT_TYPE,
              1,
              "marker_notification",
              UUID.fromString("50000000-0000-4000-8000-000000000801"),
              Instant.now(),
              Map.of(
                  "id", "50000000-0000-4000-8000-000000000801", "status", "REQUESTED", "version",
                  1));

      hub.publish(request);

      assertThat(hub.hasNoPublishFor(eventId)).isFalse();
      assertThat(hub.findByEventId(eventId)).isPresent();
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  4. 상태 초기화 / hasNoPublishFor
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("상태 초기화와 미발행 확인")
  class ResetAndAbsence {

    @Test
    @DisplayName("resetClearsAllState — publish 후 reset 호출 시 getPublishCount() == 0")
    void resetClearsAllState() {
      PublishRequest request =
          new PublishRequest(
              EventFixtures.SC08_EVENT_ID,
              EventFixtures.INCIDENT_ID_01,
              EventFixtures.SC08_EVENT_TYPE,
              1,
              "marker_notification",
              UUID.fromString("50000000-0000-4000-8000-000000000801"),
              Instant.now(),
              Map.of(
                  "id", "50000000-0000-4000-8000-000000000801", "status", "REQUESTED", "version",
                  1));
      hub.publish(request);
      assertThat(hub.getPublishCount()).isEqualTo(1);

      hub.reset();

      assertThat(hub.getPublishCount()).isEqualTo(0);
      assertThat(hub.hasNoPublishFor(EventFixtures.SC08_EVENT_ID)).isTrue();
    }

    @Test
    @DisplayName("hasNoPublishForReturnsTrueWhenNotPublished — 미발행 eventId에 대해 hasNoPublishFor() == true")
    void hasNoPublishForReturnsTrueWhenNotPublished() {
      assertThat(hub.hasNoPublishFor(EventFixtures.DEDUPE_EVENT_ID)).isTrue();
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  5. BaseEvent envelope 형식 — boundaries.md §9.1
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("BaseEvent envelope 형식 (boundaries.md §9.1)")
  class EnvelopeShape {

    /**
     * boundaries.md §9.1 BaseEvent envelope 형식:
     *
     * <pre>
     * {
     *   "eventId": "uuid",
     *   "incidentId": "uuid",
     *   "type": "PATH_APPENDED",
     *   "schemaVersion": 1,
     *   "serverTs": "2026-04-27T00:00:00Z",
     *   "payload": {}
     * }
     * </pre>
     *
     * CapturedPublish는 event_dispatch_job 저장에 필요한 모든 메타 필드를 캡처한다.
     */
    @Test
    @DisplayName("envelopeShapeMatchesBoundariesSpec — CapturedPublish에서 모든 envelope 필드 검증")
    void envelopeShapeMatchesBoundariesSpec() {
      UUID eventId = EventFixtures.SC09_EVENT_ID;
      UUID incidentId = EventFixtures.INCIDENT_ID_01;
      String type = EventFixtures.SC09_EVENT_TYPE;
      int payloadFormatVersion = 1;
      String sourceEntityType = "search_path";
      UUID sourceEntityId = UUID.fromString("30000000-0000-4000-8000-000000000501");
      Instant occurredAt = Instant.parse("2026-05-07T09:00:00Z");
      Map<String, Object> payload =
          Map.of("id", "30000000-0000-4000-8000-000000000501", "status", "RECORDING", "version", 7);

      hub.publish(
          new PublishRequest(
              eventId,
              incidentId,
              type,
              payloadFormatVersion,
              sourceEntityType,
              sourceEntityId,
              occurredAt,
              payload));

      MockEventHub.CapturedPublish captured = hub.findByEventId(eventId).orElseThrow();

      // §9.1 envelope fields — CapturedPublish에서 직접 검증
      assertThat(captured.eventId())
          .as("eventId — boundaries.md §9.1의 eventId에 대응")
          .isEqualTo(EventFixtures.SC09_EVENT_ID);
      assertThat(captured.incidentId())
          .as("incidentId — boundaries.md §9.1의 incidentId에 대응")
          .isEqualTo(EventFixtures.INCIDENT_ID_01);
      assertThat(captured.type())
          .as("type — boundaries.md §4.4 Event Catalog의 PATH_APPENDED")
          .isEqualTo("PATH_APPENDED");
      assertThat(captured.payloadFormatVersion())
          .as("payloadFormatVersion — boundaries.md §9.1의 schemaVersion (현재 1)")
          .isEqualTo(1);
      assertThat(captured.occurredAt())
          .as("occurredAt — boundaries.md §9.1의 serverTs에 대응")
          .isEqualTo(Instant.parse("2026-05-07T09:00:00Z"));
      assertThat(captured.sourceEntityType())
          .as("sourceEntityType — event_dispatch_job.source_entity_type에 대응")
          .isEqualTo("search_path");
      assertThat(captured.sourceEntityId())
          .as("sourceEntityId — event_dispatch_job.source_entity_id에 대응")
          .isEqualTo(UUID.fromString("30000000-0000-4000-8000-000000000501"));
    }
  }
}
