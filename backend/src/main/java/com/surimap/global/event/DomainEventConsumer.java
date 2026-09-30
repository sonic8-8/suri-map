package com.surimap.global.event;

/** Consumer hook invoked by the local EventPublisher after the event row is staged. */
public interface DomainEventConsumer {

  boolean supports(EventPublishRequest event);

  void consume(EventPublishRequest event);
}
