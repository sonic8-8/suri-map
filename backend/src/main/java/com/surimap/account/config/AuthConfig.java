package com.surimap.account.config;

import com.surimap.account.service.AuthSessionService;
import com.surimap.policephone.InMemoryPolicePhoneFixtureStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

  @Bean
  AuthSessionService authSessionService(InMemoryPolicePhoneFixtureStore fixtureStore) {
    return new AuthSessionService(fixtureStore);
  }
}
