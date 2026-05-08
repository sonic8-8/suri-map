package com.surimap.eventhub.validation;

import com.surimap.eventhub.dto.PublishRequest;

/**
 * BaseEvent envelope 기본 검증 유틸리티 (boundaries.md §9.1).
 *
 * <p>인스턴스화 불가능한 static 유틸리티 클래스다. {@link #validate(PublishRequest)} 호출 시 envelope 필드와 payload 공통
 * 필드(id, status, version)를 검사하며 위반 시 {@link InvalidEventEnvelopeException}을 throw한다.
 */
public final class BaseEventValidator {

  private BaseEventValidator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * PublishRequest envelope 전체를 검증한다.
   *
   * @param request 검증할 이벤트 발행 요청
   * @throws InvalidEventEnvelopeException 검증 규칙 위반 시
   */
  public static void validate(PublishRequest request) {
    // 1. eventId not null
    if (request.eventId() == null) {
      throw new InvalidEventEnvelopeException("eventId must not be null");
    }

    // 2. incidentId not null
    if (request.incidentId() == null) {
      throw new InvalidEventEnvelopeException("incidentId must not be null");
    }

    // 3. type not null, not blank
    if (request.type() == null || request.type().isBlank()) {
      throw new InvalidEventEnvelopeException("type must not be null or blank");
    }

    // 4. payloadFormatVersion == 1
    if (request.payloadFormatVersion() != 1) {
      throw new InvalidEventEnvelopeException(
          "payloadFormatVersion must be 1, but was: " + request.payloadFormatVersion());
    }

    // 5. occurredAt not null
    if (request.occurredAt() == null) {
      throw new InvalidEventEnvelopeException("occurredAt must not be null");
    }

    // 6. sourceEntityType not null, not blank
    if (request.sourceEntityType() == null || request.sourceEntityType().isBlank()) {
      throw new InvalidEventEnvelopeException("sourceEntityType must not be null or blank");
    }

    // 7. sourceEntityId not null
    if (request.sourceEntityId() == null) {
      throw new InvalidEventEnvelopeException("sourceEntityId must not be null");
    }

    // 8. payload not null
    if (request.payload() == null) {
      throw new InvalidEventEnvelopeException("payload must not be null");
    }

    // 9. payload contains 'id' and value is not null
    if (!request.payload().containsKey("id") || request.payload().get("id") == null) {
      throw new InvalidEventEnvelopeException("payload must contain non-null 'id' field");
    }

    // 10. payload contains 'status' and value is not null
    if (!request.payload().containsKey("status") || request.payload().get("status") == null) {
      throw new InvalidEventEnvelopeException("payload must contain non-null 'status' field");
    }

    // 11. payload contains 'version' and value is not null
    if (!request.payload().containsKey("version") || request.payload().get("version") == null) {
      throw new InvalidEventEnvelopeException("payload must contain non-null 'version' field");
    }
  }
}
