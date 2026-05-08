package com.surimap.eventhub.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surimap.eventhub.dto.PublishRequest;
import java.time.Instant;
import java.util.UUID;

/**
 * event_dispatch_job 테이블 INSERT용 row DTO.
 *
 * <p>S4.json §domain_model.entities[0]: event_dispatch_job outbox row.
 * PublishRequest를 DB row로 변환하는 역할을 담당한다.
 */
public record EventDispatchJobRow(
    UUID eventId,
    UUID incidentId,
    String eventType,
    int payloadFormatVersion,
    String payloadJson,
    String sourceEntityType,
    UUID sourceEntityId,
    Instant occurredAt) {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  /**
   * PublishRequest를 EventDispatchJobRow로 변환한다.
   *
   * @param request 변환할 PublishRequest
   * @return DB INSERT에 사용할 EventDispatchJobRow
   */
  public static EventDispatchJobRow from(PublishRequest request) {
    String payloadJson;
    try {
      payloadJson = OBJECT_MAPPER.writeValueAsString(request.payload());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("payload를 JSON으로 직렬화할 수 없습니다: " + e.getMessage(), e);
    }

    return new EventDispatchJobRow(
        request.eventId(),
        request.incidentId(),
        request.type(),
        request.payloadFormatVersion(),
        payloadJson,
        request.sourceEntityType(),
        request.sourceEntityId(),
        request.occurredAt());
  }
}
