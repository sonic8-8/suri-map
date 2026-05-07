package com.surimap.path.testdouble;

import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import java.util.ArrayList;
import java.util.List;

public final class CapturingSearchPathEventPublisher implements SearchPathEventPublisher {

  private final List<SearchPathPublishRequest> captured = new ArrayList<>();

  @Override
  public void publish(SearchPathPublishRequest request) {
    captured.add(request);
  }

  public List<SearchPathPublishRequest> captured() {
    return List.copyOf(captured);
  }
}
