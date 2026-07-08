package com.surimap.api.service.path;

import org.springframework.stereotype.Component;

@Component
public class NoopPathEventPublisher implements PathEventPublisher {
  @Override
  public void publishPathAppended(PathAppendedPublishRequest request) {}

  @Override
  public void publishSegmentUpdated(SearchPathSegmentUpdatedPublishRequest request) {}
}
