package com.surimap.global.sse;

import com.surimap.global.event.EventPublishRequest;
import java.util.Objects;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ServerSentEventMessage {
  private final String id;
  private final String event;
  private final EventPublishRequest data;

  @Builder
  private ServerSentEventMessage(String id, String event, EventPublishRequest data) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.event = Objects.requireNonNull(event, "event must not be null");
    this.data = Objects.requireNonNull(data, "data must not be null");
  }

  public static ServerSentEventMessage from(ServerSentEventJob replayEvent) {
    return ServerSentEventMessage.builder()
        .id(Long.toString(replayEvent.getServerSentEventSequence()))
        .event(replayEvent.getEventType())
        .data(replayEvent.toPublishRequest())
        .build();
  }
}
