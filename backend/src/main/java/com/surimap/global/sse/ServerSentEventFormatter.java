package com.surimap.global.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class ServerSentEventFormatter {

  private static final JsonMapper JSON_MAPPER =
      JsonMapper.builder()
          .findAndAddModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private ServerSentEventFormatter() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static String format(ServerSentEventMessage message) {
    String event =
        "event:%s%ndata:%s%n%n".formatted(message.getEvent(), toJson(message.getData()));
    // id 줄이 없으면 브라우저의 마지막 이벤트 ID가 바뀌지 않는다.
    return message.isSequenced() ? "id:%s%n".formatted(message.getId()) + event : event;
  }

  private static String toJson(Object data) {
    try {
      return JSON_MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("sse_frame_serialization_failed", e);
    }
  }
}
