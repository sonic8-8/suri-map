package com.surimap.path;

import org.springframework.stereotype.Component;

@Component
public class NoopPathEventPublisher implements PathEventPublisher {
  @Override
  public void publishPathAppended(PathAppendedPublishRequest request) {}
}
