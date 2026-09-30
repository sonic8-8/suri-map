package com.surimap.global.event;

/** 이벤트 공통 메타데이터와 payload의 id·status·version 존재 여부를 검사한다. */
public final class EventPublishRequestValidator {

  private EventPublishRequestValidator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * EventPublishRequest envelope 전체를 검증한다.
   *
   * @param request 검증할 이벤트 발행 요청
   * @throws InvalidEventPublishRequestException 검증 규칙 위반 시
   */
  public static void validate(EventPublishRequest request) {
    // 1. eventId not null
    if (request.getEventId() == null) {
      throw new InvalidEventPublishRequestException("eventId must not be null");
    }

    // 2. incidentId not null
    if (request.getIncidentId() == null) {
      throw new InvalidEventPublishRequestException("incidentId must not be null");
    }

    // 3. type not null, not blank
    if (request.getType() == null || request.getType().isBlank()) {
      throw new InvalidEventPublishRequestException("type must not be null or blank");
    }

    // 4. payloadFormatVersion == 1
    if (request.getPayloadFormatVersion() != 1) {
      throw new InvalidEventPublishRequestException(
          "payloadFormatVersion must be 1, but was: " + request.getPayloadFormatVersion());
    }

    // 5. occurredAt not null
    if (request.getOccurredAt() == null) {
      throw new InvalidEventPublishRequestException("occurredAt must not be null");
    }

    // 6. sourceEntityType not null, not blank
    if (request.getSourceEntityType() == null || request.getSourceEntityType().isBlank()) {
      throw new InvalidEventPublishRequestException("sourceEntityType must not be null or blank");
    }

    // 7. sourceEntityId not null
    if (request.getSourceEntityId() == null) {
      throw new InvalidEventPublishRequestException("sourceEntityId must not be null");
    }

    // 8. payload not null
    if (request.getPayload() == null) {
      throw new InvalidEventPublishRequestException("payload must not be null");
    }

    // 9. payload contains 'id' and value is not null
    if (!request.getPayload().containsKey("id") || request.getPayload().get("id") == null) {
      throw new InvalidEventPublishRequestException("payload must contain non-null 'id' field");
    }

    // 10. payload contains 'status' and value is not null
    if (!request.getPayload().containsKey("status") || request.getPayload().get("status") == null) {
      throw new InvalidEventPublishRequestException("payload must contain non-null 'status' field");
    }

    // 11. payload contains 'version' and value is not null
    if (!request.getPayload().containsKey("version")
        || request.getPayload().get("version") == null) {
      throw new InvalidEventPublishRequestException(
          "payload must contain non-null 'version' field");
    }
  }
}
