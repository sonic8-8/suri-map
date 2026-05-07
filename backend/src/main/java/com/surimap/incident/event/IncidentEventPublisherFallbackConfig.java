package com.surimap.incident.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IncidentEventPublisherFallbackConfig {

  // S4 production bean이 들어오면 이 bean은 등록되지 않는다.
  @Bean
  @ConditionalOnMissingBean(IncidentEventPublisher.class)
  IncidentEventPublisher blockingIncidentEventPublisher() {
    return new BlockingIncidentEventPublisher();
  }
}
