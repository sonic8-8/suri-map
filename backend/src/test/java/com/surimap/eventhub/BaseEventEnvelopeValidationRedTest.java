package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.fixture.EventFixtures;
import com.surimap.eventhub.validation.BaseEventValidator;
import com.surimap.eventhub.validation.InvalidEventEnvelopeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * BaseEvent envelope 기본 검증 RED 테스트 (L2-T05 / S14P31C106-159).
 *
 * <p>아직 존재하지 않는 {@code BaseEventValidator}를 대상으로 작성한 RED 테스트다. {@code BaseEventValidator} 구현 전에는
 * 컴파일 에러로 실패한다.
 *
 * <p>참조:
 *
 * <ul>
 *   <li>docs/spec/boundaries.md §9.1 BaseEvent envelope
 *   <li>docs/spec/boundaries.md §4.4 Event Catalog 공통 payload 필드
 *   <li>docs/spec/specs/S4.json §harness_fixtures
 * </ul>
 */
@DisplayName("BaseEvent envelope 기본 검증")
class BaseEventEnvelopeValidationRedTest {

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

  /** 유효한 PublishRequest를 생성한다. 개별 필드 교체에 활용한다. */
  private PublishRequest validRequest() {
    return new PublishRequest(
        VALID_EVENT_ID,
        VALID_INCIDENT_ID,
        VALID_TYPE,
        VALID_SCHEMA_VERSION,
        VALID_SOURCE_ENTITY_TYPE,
        VALID_SOURCE_ENTITY_ID,
        VALID_OCCURRED_AT,
        VALID_PAYLOAD);
  }

  // ═══════════════════════════════════════════════════════════
  //  1. 유효한 envelope 통과
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("유효한 envelope 통과")
  class ValidEnvelope {

    @Test
    @DisplayName("모든 필드가 유효하면 validate() 호출 시 예외가 발생하지 않는다")
    void validEnvelopePassesWithoutException() {
      PublishRequest request = validRequest();

      assertThatCode(() -> BaseEventValidator.validate(request)).doesNotThrowAnyException();
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  2. envelope 식별자 필드 null 검증
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("envelope 식별자 필드 null 검증")
  class IdentifierFieldsNullValidation {

    @Test
    @DisplayName("eventId가 null이면 InvalidEventEnvelopeException이 발생한다")
    void nullEventIdThrowsException() {
      PublishRequest request =
          new PublishRequest(
              null,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("incidentId가 null이면 InvalidEventEnvelopeException이 발생한다")
    void nullIncidentIdThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              null,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("sourceEntityId가 null이면 InvalidEventEnvelopeException이 발생한다")
    void nullSourceEntityIdThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              null,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  3. type 필드 검증
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("type 필드 검증")
  class TypeFieldValidation {

    @Test
    @DisplayName("type이 null이면 InvalidEventEnvelopeException이 발생한다")
    void nullTypeThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              null,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("type이 blank 문자열이면 InvalidEventEnvelopeException이 발생한다")
    void blankTypeThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              "   ",
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  4. payloadFormatVersion(schemaVersion) 검증
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("payloadFormatVersion 검증 (현재 모든 이벤트는 1)")
  class PayloadFormatVersionValidation {

    @Test
    @DisplayName("payloadFormatVersion이 0이면 InvalidEventEnvelopeException이 발생한다")
    void versionZeroThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              0,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("payloadFormatVersion이 2이면 InvalidEventEnvelopeException이 발생한다")
    void versionTwoThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              2,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  5. 시간·출처 필드 null 검증
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("시간·출처 필드 null 검증")
  class TemporalAndSourceFieldsNullValidation {

    @Test
    @DisplayName("occurredAt이 null이면 InvalidEventEnvelopeException이 발생한다")
    void nullOccurredAtThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              null,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("sourceEntityType이 null이면 InvalidEventEnvelopeException이 발생한다")
    void nullSourceEntityTypeThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              null,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              VALID_PAYLOAD);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }
  }

  // ═══════════════════════════════════════════════════════════
  //  6. payload 존재 및 공통 필드 검증
  // ═══════════════════════════════════════════════════════════

  @Nested
  @DisplayName("payload 존재 및 공통 필드 검증 (boundaries.md §4.4)")
  class PayloadCommonFieldsValidation {

    @Test
    @DisplayName("payload가 null이면 InvalidEventEnvelopeException이 발생한다")
    void nullPayloadThrowsException() {
      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              null);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("payload에 'id' 키가 없으면 InvalidEventEnvelopeException이 발생한다")
    void payloadMissingIdKeyThrowsException() {
      Map<String, Object> payload = new HashMap<>();
      payload.put("status", "RECORDING");
      payload.put("version", 7);

      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              payload);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("payload에 'status' 키가 없으면 InvalidEventEnvelopeException이 발생한다")
    void payloadMissingStatusKeyThrowsException() {
      Map<String, Object> payload = new HashMap<>();
      payload.put("id", "30000000-0000-4000-8000-000000000501");
      payload.put("version", 7);

      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              payload);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }

    @Test
    @DisplayName("payload에 'version' 키가 없으면 InvalidEventEnvelopeException이 발생한다")
    void payloadMissingVersionKeyThrowsException() {
      Map<String, Object> payload = new HashMap<>();
      payload.put("id", "30000000-0000-4000-8000-000000000501");
      payload.put("status", "RECORDING");

      PublishRequest request =
          new PublishRequest(
              VALID_EVENT_ID,
              VALID_INCIDENT_ID,
              VALID_TYPE,
              VALID_SCHEMA_VERSION,
              VALID_SOURCE_ENTITY_TYPE,
              VALID_SOURCE_ENTITY_ID,
              VALID_OCCURRED_AT,
              payload);

      assertThatThrownBy(() -> BaseEventValidator.validate(request))
          .isInstanceOf(InvalidEventEnvelopeException.class);
    }
  }
}
