package com.surimap.eventhub.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surimap.eventhub.dto.PublishRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 저장한 이벤트 내용과 전송 상태, 사건별 SSE 순번을 보관하는 전송 작업. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventDispatchJob {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

  private UUID id;
  private UUID eventId;
  private UUID incidentId;
  private String eventType;
  private int payloadFormatVersion;
  private String payloadJson;
  private String sourceEntityType;
  private UUID sourceEntityId;
  private Instant occurredAt;
  private String dispatchStatus;
  private Long sseSequence;

  public static EventDispatchJob from(PublishRequest request) {
    String payloadJson;
    try {
      payloadJson = OBJECT_MAPPER.writeValueAsString(request.payload());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("payload를 JSON으로 직렬화할 수 없습니다", exception);
    }
    return new EventDispatchJob(
        UUID.randomUUID(),
        request.eventId(),
        request.incidentId(),
        request.type(),
        request.payloadFormatVersion(),
        payloadJson,
        request.sourceEntityType(),
        request.sourceEntityId(),
        request.occurredAt(),
        "PENDING",
        null);
  }

  public PublishRequest toPublishRequest() {
    return new PublishRequest(
        eventId,
        incidentId,
        eventType,
        payloadFormatVersion,
        sourceEntityType,
        sourceEntityId,
        occurredAt,
        readPayload());
  }

  private Map<String, Object> readPayload() {
    try {
      return OBJECT_MAPPER.readValue(payloadJson, PAYLOAD_TYPE);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "event_dispatch_job payload를 JSON으로 역직렬화할 수 없습니다", exception);
    }
  }
}
