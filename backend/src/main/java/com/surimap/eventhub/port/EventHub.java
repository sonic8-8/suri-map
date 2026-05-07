package com.surimap.eventhub.port;

import com.surimap.eventhub.dto.PublishRequest;

public interface EventHub {
  void publish(PublishRequest request);
}
