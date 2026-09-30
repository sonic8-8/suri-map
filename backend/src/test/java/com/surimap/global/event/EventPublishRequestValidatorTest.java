package com.surimap.global.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.fixture.EventFixtures;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EventPublishRequestValidatorTest {

  private static final UUID VALID_EVENT_ID = EventFixtures.SC09_EVENT_ID;
  private static final UUID VALID_INCIDENT_ID = EventFixtures.INCIDENT_ID_01;
  private static final String VALID_TYPE = EventFixtures.SC09_EVENT_TYPE;
  private static final int VALID_SCHEMA_VERSION = 1;
  private static final String VALID_SOURCE_ENTITY_TYPE = "search_path";
  private static final UUID VALID_SOURCE_ENTITY_ID =
      UUID.fromString("30000000-0000-4000-8000-000000000501");
  private static final Instant VALID_OCCURRED_AT = Instant.parse("2026-05-08T00:00:00Z");
  private static final Map<String, Object> VALID_PAYLOAD =
      Map.of(
          "id", "30000000-0000-4000-8000-000000000501",
          "status", "RECORDING",
          "version", 7);

  private EventPublishRequest validRequest() {
    return EventPublishRequest.builder()
        .eventId(VALID_EVENT_ID)
        .incidentId(VALID_INCIDENT_ID)
        .type(VALID_TYPE)
        .payloadFormatVersion(VALID_SCHEMA_VERSION)
        .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
        .sourceEntityId(VALID_SOURCE_ENTITY_ID)
        .occurredAt(VALID_OCCURRED_AT)
        .payload(VALID_PAYLOAD)
        .build();
  }

  //  1. 유효한 envelope 통과

  @Nested
  class ValidEnvelope {

    @Test
    @DisplayName("모든 필드가 유효하면 validate() 호출 시 예외가 발생하지 않는다")
    void valid_envelope_passes_without_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request = validRequest();

      // when / then: 유효한 발행 입력은 검증을 통과한다.
      assertThatCode(() -> EventPublishRequestValidator.validate(request))
          .doesNotThrowAnyException();
    }
  }

  //  2. envelope 식별자 필드 null 검증

  @Nested
  class IdentifierFieldsNullValidation {

    @Test
    @DisplayName("eventId가 null이면 InvalidEventPublishRequestException이 발생한다")
    void null_event_id_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(null)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("incidentId가 null이면 InvalidEventPublishRequestException이 발생한다")
    void null_incident_id_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(null)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("sourceEntityId가 null이면 InvalidEventPublishRequestException이 발생한다")
    void null_source_entity_id_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(null)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }
  }

  //  3. type 필드 검증

  @Nested
  class TypeFieldValidation {

    @Test
    @DisplayName("type이 null이면 InvalidEventPublishRequestException이 발생한다")
    void null_type_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(null)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("type이 blank 문자열이면 InvalidEventPublishRequestException이 발생한다")
    void blank_type_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type("   ")
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }
  }

  //  4. payloadFormatVersion(schemaVersion) 검증

  @Nested
  class PayloadFormatVersionValidation {

    @Test
    @DisplayName("payloadFormatVersion이 0이면 InvalidEventPublishRequestException이 발생한다")
    void version_zero_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(0)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("payloadFormatVersion이 2이면 InvalidEventPublishRequestException이 발생한다")
    void version_two_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(2)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }
  }

  //  5. 시간·출처 필드 null 검증

  @Nested
  class TemporalAndSourceFieldsNullValidation {

    @Test
    @DisplayName("occurredAt이 null이면 InvalidEventPublishRequestException이 발생한다")
    void null_occurred_at_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(null)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("sourceEntityType이 null이면 InvalidEventPublishRequestException이 발생한다")
    void null_source_entity_type_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(null)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(VALID_PAYLOAD)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }
  }

  //  6. payload 존재 및 공통 필드 검증

  @Nested
  class PayloadCommonFieldsValidation {

    @Test
    @DisplayName("payload가 null이면 InvalidEventPublishRequestException이 발생한다")
    void null_payload_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(null)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("payload에 'id' 키가 없으면 InvalidEventPublishRequestException이 발생한다")
    void payload_missing_id_key_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      Map<String, Object> payload = new HashMap<>();
      payload.put("status", "RECORDING");
      payload.put("version", 7);

      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(payload)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("payload에 'status' 키가 없으면 InvalidEventPublishRequestException이 발생한다")
    void payload_missing_status_key_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      Map<String, Object> payload = new HashMap<>();
      payload.put("id", "30000000-0000-4000-8000-000000000501");
      payload.put("version", 7);

      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(payload)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }

    @Test
    @DisplayName("payload에 'version' 키가 없으면 InvalidEventPublishRequestException이 발생한다")
    void payload_missing_version_key_throws_exception() {
      // given: 검사할 발행 입력을 준비한다.
      Map<String, Object> payload = new HashMap<>();
      payload.put("id", "30000000-0000-4000-8000-000000000501");
      payload.put("status", "RECORDING");

      EventPublishRequest request =
          EventPublishRequest.builder()
              .eventId(VALID_EVENT_ID)
              .incidentId(VALID_INCIDENT_ID)
              .type(VALID_TYPE)
              .payloadFormatVersion(VALID_SCHEMA_VERSION)
              .sourceEntityType(VALID_SOURCE_ENTITY_TYPE)
              .sourceEntityId(VALID_SOURCE_ENTITY_ID)
              .occurredAt(VALID_OCCURRED_AT)
              .payload(payload)
              .build();

      // when / then: 공통 형식이 잘못된 입력은 검증에서 거부한다.
      assertThatThrownBy(() -> EventPublishRequestValidator.validate(request))
          .isInstanceOf(InvalidEventPublishRequestException.class);
    }
  }
}
