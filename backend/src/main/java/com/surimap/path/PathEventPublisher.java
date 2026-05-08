package com.surimap.path;

public interface PathEventPublisher {
  void publishPathAppended(PathAppendedPublishRequest request);

  void publishSegmentUpdated(SearchPathSegmentUpdatedPublishRequest request);
}
