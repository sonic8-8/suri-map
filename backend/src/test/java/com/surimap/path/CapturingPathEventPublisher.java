package com.surimap.path;

import java.util.ArrayList;
import java.util.List;

public class CapturingPathEventPublisher implements PathEventPublisher {
  private final List<PathAppendedPublishRequest> published = new ArrayList<>();
  private final List<SearchPathSegmentUpdatedPublishRequest> segmentUpdated = new ArrayList<>();

  @Override
  public void publishPathAppended(PathAppendedPublishRequest request) {
    published.add(request);
  }

  public List<PathAppendedPublishRequest> published() {
    return List.copyOf(published);
  }

  @Override
  public void publishSegmentUpdated(SearchPathSegmentUpdatedPublishRequest request) {
    segmentUpdated.add(request);
  }

  public List<SearchPathSegmentUpdatedPublishRequest> segmentUpdated() {
    return List.copyOf(segmentUpdated);
  }
}
