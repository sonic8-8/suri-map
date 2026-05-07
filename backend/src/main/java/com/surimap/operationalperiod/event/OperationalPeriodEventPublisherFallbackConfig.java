package com.surimap.operationalperiod.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OperationalPeriodEventPublisherFallbackConfig {

  // S4 production bean이 들어오면 이 bean은 등록되지 않는다.
  @Bean
  @ConditionalOnMissingBean(EventPublisherPort.class)
  EventPublisherPort blockingOperationalPeriodEventPublisher() {
    return new BlockingOperationalPeriodEventPublisher();
  }
}
