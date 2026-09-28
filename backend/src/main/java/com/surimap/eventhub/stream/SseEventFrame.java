package com.surimap.eventhub.stream;

import com.surimap.eventhub.adapter.EventDispatchJob;
import com.surimap.eventhub.dto.PublishRequest;
import java.util.Objects;

public record SseEventFrame(String id, String event, PublishRequest data) {

  public SseEventFrame {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(event, "event must not be null");
    Objects.requireNonNull(data, "data must not be null");
  }

  static SseEventFrame from(EventDispatchJob replayEvent) {
    return new SseEventFrame(
        Long.toString(replayEvent.getSseSequence()),
        replayEvent.getEventType(),
        replayEvent.toPublishRequest());
  }
}
