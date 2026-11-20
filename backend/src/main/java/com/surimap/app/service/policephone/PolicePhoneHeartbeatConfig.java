package com.surimap.app.service.policephone;

import com.surimap.global.event.CapturingEventPublisher;
import com.surimap.global.event.EventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicePhoneHeartbeatConfig {

  @Bean
  @ConditionalOnMissingBean(EventPublisher.class)
  CapturingEventPublisher eventHub() {
    return new CapturingEventPublisher();
  }
}
