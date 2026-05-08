package com.surimap.eventhub.stream;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SseReplayService {

  private final SseReplayEventStore replayEventStore;

  public SseReplayService(SseReplayEventStore replayEventStore) {
    this.replayEventStore =
        Objects.requireNonNull(replayEventStore, "replayEventStore must not be null");
  }

  public List<SseEventFrame> replayAfter(UUID incidentId, String lastEventId) {
    long cursor = parseCursor(lastEventId);
    var replayEvents = replayEventStore.replayAfter(incidentId, cursor);
    if (cursor > 0
        && !replayEvents.isEmpty()
        && replayEvents.get(0).replaySequence() > cursor + 1) {
      throw new GoneRefetchRequiredException();
    }
    return replayEvents.stream().map(SseEventFrame::from).toList();
  }

  SseEventFrame frameOf(SseReplayEvent event) {
    return SseEventFrame.from(event);
  }

  private long parseCursor(String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return 0L;
    }
    try {
      long parsed = Long.parseLong(lastEventId);
      if (parsed < 0) {
        throw new GoneRefetchRequiredException();
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new GoneRefetchRequiredException();
    }
  }
}
