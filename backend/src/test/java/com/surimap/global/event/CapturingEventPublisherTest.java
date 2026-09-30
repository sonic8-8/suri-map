package com.surimap.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class CapturingEventPublisherTest {

  private CapturingEventPublisher hub;

  @BeforeEach
  void set_up() {
    hub = new CapturingEventPublisher();
  }

  //  1. 기본 publish 캡처

  @Nested
  class PublishCapture {

    @Test
    @DisplayName("이벤트를 발행하면 같은 입력 객체를 기록하고 이벤트 ID로 조회한다")
    void publish_captures_request() {
      // given: 기록할 이벤트의 식별자·시각·내용을 준비한다.
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

      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(eventId)
              .incidentId(incidentId)
              .type(type)
              .payloadFormatVersion(payloadFormatVersion)
              .sourceEntityType(sourceEntityType)
              .sourceEntityId(sourceEntityId)
              .occurredAt(occurredAt)
              .payload(payload)
              .build();

      // when: 이벤트를 발행한다.
      hub.publish(request);

      // then: 복제한 별도 객체가 아니라 원래 입력과 모든 필드를 유지한다.
      Optional<EventPublishRequest> found = hub.findByEventId(eventId);
      assertThat(found).isPresent();
      assertThat(found.orElseThrow()).isSameAs(request);

      EventPublishRequest captured = found.get();
      assertThat(captured.getEventId()).isEqualTo(eventId);
      assertThat(captured.getIncidentId()).isEqualTo(incidentId);
      assertThat(captured.getType()).isEqualTo(type);
      assertThat(captured.getPayloadFormatVersion()).isEqualTo(1);
      assertThat(captured.getPayload())
          .containsEntry("id", "50000000-0000-4000-8000-000000000801")
          .containsEntry("status", "REQUESTED")
          .containsEntry("version", 1);
    }

    @Test
    @DisplayName("서로 다른 이벤트를 발행하면 두 요청을 모두 기록한다")
    void publish_captures_multiple_requests() {
      // given: 서로 다른 두 이벤트를 준비한다.
      EventPublishRequest sc08 =
          EventPublishRequest.builder()
              .eventId(EventFixtures.SC08_EVENT_ID)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC08_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("marker_notification")
              .sourceEntityId(UUID.fromString("50000000-0000-4000-8000-000000000801"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "50000000-0000-4000-8000-000000000801",
                      "status",
                      "REQUESTED",
                      "version",
                      1))
              .build();

      EventPublishRequest sc09 =
          EventPublishRequest.builder()
              .eventId(EventFixtures.SC09_EVENT_ID)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC09_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("search_path")
              .sourceEntityId(UUID.fromString("30000000-0000-4000-8000-000000000501"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "30000000-0000-4000-8000-000000000501",
                      "status",
                      "RECORDING",
                      "version",
                      7))
              .build();

      hub.publish(sc08);
      hub.publish(sc09);

      assertThat(hub.getPublishCount()).isEqualTo(2);
    }
  }

  //  2. 타입/사건 기반 조회

  @Nested
  class QueryByTypeAndIncident {

    @Test
    @DisplayName("이벤트 유형으로 조회하면 해당 유형의 발행 기록을 반환한다")
    void find_by_type_returns_matching_requests() {
      // given: 조회할 유형의 이벤트를 기록한다.
      EventPublishRequest sc08 =
          EventPublishRequest.builder()
              .eventId(EventFixtures.SC08_EVENT_ID)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC08_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("marker_notification")
              .sourceEntityId(UUID.fromString("50000000-0000-4000-8000-000000000801"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "50000000-0000-4000-8000-000000000801",
                      "status",
                      "REQUESTED",
                      "version",
                      1))
              .build();
      hub.publish(sc08);

      List<EventPublishRequest> results = hub.findByType(EventFixtures.SC08_EVENT_TYPE);

      assertThat(results).hasSize(1);
      assertThat(results.get(0).getType()).isEqualTo(EventFixtures.SC08_EVENT_TYPE);
      assertThat(results.get(0).getEventId()).isEqualTo(EventFixtures.SC08_EVENT_ID);
    }

    @Test
    @DisplayName("사건 ID로 조회하면 해당 사건의 발행 기록을 반환한다")
    void find_by_incident_id_returns_matching_requests() {
      // given: 같은 사건의 두 이벤트를 기록한다.
      EventPublishRequest sc08 =
          EventPublishRequest.builder()
              .eventId(EventFixtures.SC08_EVENT_ID)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC08_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("marker_notification")
              .sourceEntityId(UUID.fromString("50000000-0000-4000-8000-000000000801"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "50000000-0000-4000-8000-000000000801",
                      "status",
                      "REQUESTED",
                      "version",
                      1))
              .build();
      EventPublishRequest sc09 =
          EventPublishRequest.builder()
              .eventId(EventFixtures.SC09_EVENT_ID)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC09_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("search_path")
              .sourceEntityId(UUID.fromString("30000000-0000-4000-8000-000000000501"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "30000000-0000-4000-8000-000000000501",
                      "status",
                      "RECORDING",
                      "version",
                      7))
              .build();
      hub.publish(sc08);
      hub.publish(sc09);

      List<EventPublishRequest> results = hub.findByIncidentId(EventFixtures.INCIDENT_ID_01);

      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(EventPublishRequest::getIncidentId)
          .containsOnly(EventFixtures.INCIDENT_ID_01);
    }
  }

  //  3. Failure injection

  @Nested
  class FailureInjection {

    @Test
    @DisplayName("실패를 지정한 이벤트는 발행을 거부하고 기록하지 않는다")
    void failure_injection_blocks_publish() {
      // given: 특정 이벤트의 발행이 실패하도록 설정한다.
      UUID eventId = EventFixtures.SC08_EVENT_ID;
      hub.injectFailureFor(eventId);

      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(eventId)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC08_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("marker_notification")
              .sourceEntityId(UUID.fromString("50000000-0000-4000-8000-000000000801"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "50000000-0000-4000-8000-000000000801",
                      "status",
                      "REQUESTED",
                      "version",
                      1))
              .build();

      // when / then: 발행 시 예외가 발생하고 기록도 남지 않는다.
      assertThatThrownBy(() -> hub.publish(request)).isInstanceOf(RuntimeException.class);

      assertThat(hub.hasNoPublishFor(eventId)).isTrue();
    }

    @Test
    @DisplayName("실패 지정을 해제하면 해당 이벤트를 다시 발행할 수 있다")
    void clear_failure_injection_allows_publish() {
      // given: 이벤트의 실패 지정을 해제한다.
      UUID eventId = EventFixtures.SC08_EVENT_ID;
      hub.injectFailureFor(eventId);
      hub.clearFailureInjection(eventId);

      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(eventId)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC08_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("marker_notification")
              .sourceEntityId(UUID.fromString("50000000-0000-4000-8000-000000000801"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "50000000-0000-4000-8000-000000000801",
                      "status",
                      "REQUESTED",
                      "version",
                      1))
              .build();

      hub.publish(request);

      assertThat(hub.hasNoPublishFor(eventId)).isFalse();
      assertThat(hub.findByEventId(eventId)).isPresent();
    }
  }

  //  4. 상태 초기화 / hasNoPublishFor

  @Nested
  class ResetAndAbsence {

    @Test
    @DisplayName("초기화하면 기존 발행 기록을 비운다")
    void reset_clears_all_state() {
      // given: 발행 기록이 하나 있다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(EventFixtures.SC08_EVENT_ID)
              .incidentId(EventFixtures.INCIDENT_ID_01)
              .type(EventFixtures.SC08_EVENT_TYPE)
              .payloadFormatVersion(1)
              .sourceEntityType("marker_notification")
              .sourceEntityId(UUID.fromString("50000000-0000-4000-8000-000000000801"))
              .occurredAt(Instant.now())
              .payload(
                  Map.of(
                      "id",
                      "50000000-0000-4000-8000-000000000801",
                      "status",
                      "REQUESTED",
                      "version",
                      1))
              .build();
      hub.publish(request);
      assertThat(hub.getPublishCount()).isEqualTo(1);

      // when: 기록용 발행자를 초기화한다.
      hub.reset();

      // then: 기존 기록이 조회되지 않는다.
      assertThat(hub.getPublishCount()).isEqualTo(0);
      assertThat(hub.hasNoPublishFor(EventFixtures.SC08_EVENT_ID)).isTrue();
    }

    @Test
    @DisplayName(
        "hasNoPublishForReturnsTrueWhenNotPublished — 미발행 eventId에 대해 hasNoPublishFor() == true")
    void has_no_publish_for_returns_true_when_not_published() {
      assertThat(hub.hasNoPublishFor(EventFixtures.DEDUPE_EVENT_ID)).isTrue();
    }
  }

  //  5. BaseEvent envelope 형식 — boundaries.md §9.1

  @Nested
  class EnvelopeShape {

    @Test
    @DisplayName("발행 기록은 이벤트의 공통 메타데이터를 유지한다")
    void envelope_shape_matches_boundaries_spec() {
      // given: 원본 메타데이터를 지정한 이벤트를 준비한다.
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
          EventPublishRequest.builder()
              .eventId(eventId)
              .incidentId(incidentId)
              .type(type)
              .payloadFormatVersion(payloadFormatVersion)
              .sourceEntityType(sourceEntityType)
              .sourceEntityId(sourceEntityId)
              .occurredAt(occurredAt)
              .payload(payload)
              .build());

      EventPublishRequest captured = hub.findByEventId(eventId).orElseThrow();

      // §9.1 envelope fields — EventPublishRequest에서 직접 검증
      assertThat(captured.getEventId())
          .as("eventId — boundaries.md §9.1의 eventId에 대응")
          .isEqualTo(EventFixtures.SC09_EVENT_ID);
      assertThat(captured.getIncidentId())
          .as("incidentId — boundaries.md §9.1의 incidentId에 대응")
          .isEqualTo(EventFixtures.INCIDENT_ID_01);
      assertThat(captured.getType())
          .as("type — boundaries.md §4.4 Event Catalog의 PATH_APPENDED")
          .isEqualTo("PATH_APPENDED");
      assertThat(captured.getPayloadFormatVersion())
          .as("payloadFormatVersion — boundaries.md §9.1의 schemaVersion (현재 1)")
          .isEqualTo(1);
      assertThat(captured.getOccurredAt())
          .as("occurredAt — boundaries.md §9.1의 serverTs에 대응")
          .isEqualTo(Instant.parse("2026-05-07T09:00:00Z"));
      assertThat(captured.getSourceEntityType())
          .as("sourceEntityType — event_dispatch_job.source_entity_type에 대응")
          .isEqualTo("search_path");
      assertThat(captured.getSourceEntityId())
          .as("sourceEntityId — event_dispatch_job.source_entity_id에 대응")
          .isEqualTo(UUID.fromString("30000000-0000-4000-8000-000000000501"));
    }
  }
}
