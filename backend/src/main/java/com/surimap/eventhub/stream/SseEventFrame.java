package com.surimap.eventhub.stream;

import com.surimap.eventhub.dto.PublishRequest;
import java.util.Objects;

public record SseEventFrame(String id, String event, PublishRequest data) {

  public SseEventFrame {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(event, "event must not be null");
    Objects.requireNonNull(data, "data must not be null");
  }

  static SseEventFrame from(SseReplayEvent replayEvent) {
    return new SseEventFrame(
        Long.toString(replayEvent.replaySequence()),
        replayEvent.envelope().type(),
        replayEvent.envelope());
  }
}
