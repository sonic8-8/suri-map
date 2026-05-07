package com.surimap.marker.port;

import com.surimap.marker.dto.MarkerPublishRequest;

public interface MarkerEventPublisher {

  void publish(MarkerPublishRequest request);
}
