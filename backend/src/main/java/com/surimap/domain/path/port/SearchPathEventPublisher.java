package com.surimap.domain.path.port;

import com.surimap.domain.path.SearchPathPublishRequest;

public interface SearchPathEventPublisher {

  void publish(SearchPathPublishRequest request);
}
