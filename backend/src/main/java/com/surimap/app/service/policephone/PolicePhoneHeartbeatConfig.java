package com.surimap.app.service.policephone;

import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.eventhub.port.EventHub;
import com.surimap.policephone.PolicePhoneHeartbeatRecorder;
import com.surimap.policephone.PolicePhonePersistenceService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicePhoneHeartbeatConfig {

  @Bean
  @ConditionalOnMissingBean(EventHub.class)
  MockEventHub eventHub() {
    return new MockEventHub();
  }

  @Bean
  AppPolicePhoneHeartbeatService appPolicePhoneHeartbeatService(
      PolicePhonePersistenceService policePhonePersistenceService, EventHub eventHub, Clock clock) {
    PolicePhoneHeartbeatRecorder recorder = policePhonePersistenceService;
    return new AppPolicePhoneHeartbeatService(recorder, eventHub, clock);
  }
}
