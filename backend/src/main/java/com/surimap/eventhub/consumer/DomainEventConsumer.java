package com.surimap.eventhub.consumer;

import com.surimap.eventhub.dto.PublishRequest;

/** Consumer hook invoked by the local EventHub after the event row is staged. */
public interface DomainEventConsumer {

  boolean supports(PublishRequest event);

  void consume(PublishRequest event);
}
