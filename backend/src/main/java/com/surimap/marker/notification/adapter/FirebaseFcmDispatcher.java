package com.surimap.marker.notification.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.marker.notification.port.FcmDispatcherPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fcm.provider", havingValue = "firebase")
public class FirebaseFcmDispatcher implements FcmDispatcherPort {

  private final FirebaseFcmSender sender;
  private final FirebaseFcmProperties properties;
  private final ObjectMapper objectMapper;

  public FirebaseFcmDispatcher(
      FirebaseFcmSender sender, FirebaseFcmProperties properties, ObjectMapper objectMapper) {
    this.sender = Objects.requireNonNull(sender, "sender must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  @Override
  public DispatchResult send(List<String> recipients, Map<String, Object> payload, String eventId) {
    Objects.requireNonNull(recipients, "recipients must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");

    List<String> tokens =
        recipients.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .distinct()
            .toList();
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("recipients must not be empty");
    }

    FirebaseFcmBatchResult result =
        sender.sendMulticast(tokens, dataPayload(payload, eventId), properties.isDryRun());
    return new DispatchResult(
        eventId, result.successCount(), result.failureCount(), result.failedRecipients());
  }

  private Map<String, String> dataPayload(Map<String, Object> payload, String eventId) {
    Map<String, String> data = new LinkedHashMap<>();
    data.put("eventId", eventId);
    payload.forEach(
        (key, value) -> {
          if (key != null && value != null) {
            data.put(key, stringify(value));
          }
        });
    return data;
  }

  private String stringify(Object value) {
    if (value instanceof String string) {
      return string;
    }
    if (value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("FCM data payload value is not serializable", exception);
    }
  }
}
