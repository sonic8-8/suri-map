package com.surimap.app.service.policephone;

import com.surimap.global.event.CapturingEventPublisher;
import com.surimap.global.event.EventPublisher;
import com.surimap.policephone.PolicePhoneHeartbeatRecorder;
import com.surimap.policephone.PolicePhonePersistenceService;
import java.time.Clock;
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

  @Bean
  AppPolicePhoneHeartbeatService appPolicePhoneHeartbeatService(
      PolicePhonePersistenceService policePhonePersistenceService,
      EventPublisher eventHub,
      Clock clock) {
    PolicePhoneHeartbeatRecorder recorder = policePhonePersistenceService;
    return new AppPolicePhoneHeartbeatService(recorder, eventHub, clock);
  }
}
