package com.surimap.api.service.path;

public interface PathEventPublisher {
  void publishPathAppended(PathAppendedPublishRequest request);

  void publishSegmentUpdated(SearchPathSegmentUpdatedPublishRequest request);
}
