package com.surimap.operationalperiod.event;

/** S4 구현체가 없을 때 OP 전환 이벤트 없는 OP 생성을 막는 fallback. */
public class BlockingOperationalPeriodEventPublisher implements EventPublisherPort {

  @Override
  public void publish(OpTransitionedPublishRequest request) {
    throw new IllegalStateException("operational_period_event_publisher_unavailable");
  }
}
