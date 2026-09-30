package com.surimap.operationalperiod.event;

/** S4 EventPublisher.publish 포트 (S8.json §api_contracts.consumed). */
public interface EventPublisherPort {

  void publish(OpTransitionedPublishRequest request);
}
