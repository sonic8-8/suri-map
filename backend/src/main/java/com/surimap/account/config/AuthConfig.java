package com.surimap.account.config;

import com.surimap.account.service.AuthSessionService;
import com.surimap.account.repository.AccountLoginMapper;
import com.surimap.policephone.InMemoryPolicePhoneFixtureStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

  @Bean
  AuthSessionService authSessionService(
      InMemoryPolicePhoneFixtureStore fixtureStore, AccountLoginMapper accountLoginMapper) {
    return new AuthSessionService(fixtureStore, accountLoginMapper);
  }
}
