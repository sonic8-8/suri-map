package com.surimap.eventhub.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surimap.eventhub.dto.PublishRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventDispatchJobDispatchRecord(
    UUID id,
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
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

  public PublishRequest toPublishRequest() {
    return new PublishRequest(
        eventId,
        incidentId,
        eventType,
        payloadFormatVersion,
        sourceEntityType,
        sourceEntityId,
        occurredAt,
        payload());
  }

  private Map<String, Object> payload() {
    try {
      return OBJECT_MAPPER.readValue(payloadJson, PAYLOAD_TYPE);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "event_dispatch_job payload를 JSON으로 역직렬화할 수 없습니다: " + exception.getMessage(),
          exception);
    }
  }
}
