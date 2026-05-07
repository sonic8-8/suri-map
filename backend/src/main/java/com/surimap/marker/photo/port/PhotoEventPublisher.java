package com.surimap.marker.photo.port;

import com.surimap.marker.photo.dto.PublishRequest;

public interface PhotoEventPublisher {

  void publish(PublishRequest request);
}
