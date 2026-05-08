package com.surimap.app.service.policephone;

import com.surimap.eventhub.adapter.MockEventHub;
import com.surimap.eventhub.port.EventHub;
import com.surimap.policephone.InMemoryPolicePhoneFixtureStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class PolicePhoneHeartbeatConfig {

  @Bean
  @Primary
  InMemoryPolicePhoneFixtureStore inMemoryPolicePhoneFixtureStore(Clock clock) {
    return new InMemoryPolicePhoneFixtureStore(clock);
  }

  @Bean
  @ConditionalOnMissingBean(EventHub.class)
  MockEventHub eventHub() {
    return new MockEventHub();
  }

  @Bean
  AppPolicePhoneHeartbeatService appPolicePhoneHeartbeatService(
      InMemoryPolicePhoneFixtureStore fixtureStore, EventHub eventHub, Clock clock) {
    return new AppPolicePhoneHeartbeatService(fixtureStore, eventHub, clock);
  }
}
