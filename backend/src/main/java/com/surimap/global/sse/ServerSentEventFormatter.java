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
    return "id:%s%nevent:%s%ndata:%s%n%n"
        .formatted(message.getId(), message.getEvent(), toJson(message.getData()));
  }

  private static String toJson(Object data) {
    try {
      return JSON_MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("sse_frame_serialization_failed", e);
    }
  }
}
