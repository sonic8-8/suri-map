package com.surimap.operationalperiod.testdouble;

import com.surimap.operationalperiod.event.EventPublisherPort;
import com.surimap.operationalperiod.event.OpTransitionedPublishRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 테스트용 in-memory event publisher. PublishRequest 캡처용. */
public final class InMemoryEventPublisher implements EventPublisherPort {

  private final List<OpTransitionedPublishRequest> captured = new ArrayList<>();

  @Override
  public void publish(OpTransitionedPublishRequest request) {
    captured.add(request);
  }

  public List<OpTransitionedPublishRequest> captured() {
    return Collections.unmodifiableList(captured);
  }

  public void clear() {
    captured.clear();
  }
}
