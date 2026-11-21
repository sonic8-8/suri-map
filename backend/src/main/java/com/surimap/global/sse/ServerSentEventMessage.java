package com.surimap.global.sse;

import com.surimap.global.event.EventPublishRequest;
import java.util.Objects;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ServerSentEventMessage {
  // 저장하지 않고 보내는 이벤트에는 순번이 없어 null이다.
  private final String id;
  private final String event;
  private final EventPublishRequest data;

  @Builder
  private ServerSentEventMessage(String id, String event, EventPublishRequest data) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.event = Objects.requireNonNull(event, "event must not be null");
    this.data = Objects.requireNonNull(data, "data must not be null");
  }

  private ServerSentEventMessage(EventPublishRequest data) {
    this.id = null;
    this.data = Objects.requireNonNull(data, "data must not be null");
    this.event = Objects.requireNonNull(data.getType(), "event must not be null");
  }

  public static ServerSentEventMessage from(ServerSentEventJob replayEvent) {
    return ServerSentEventMessage.builder()
        .id(Long.toString(replayEvent.getServerSentEventSequence()))
        .event(replayEvent.getEventType())
        .data(replayEvent.toPublishRequest())
        .build();
  }

  /** 이벤트 발송 작업으로 저장하지 않는 이벤트다. 연결의 마지막 순번과 재전송에 들어가지 않는다. */
  public static ServerSentEventMessage unsequenced(EventPublishRequest data) {
    return new ServerSentEventMessage(data);
  }

  public boolean isSequenced() {
    return id != null;
  }
}
