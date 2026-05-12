package com.surimap.account.config;

import com.surimap.account.repository.AccountLoginMapper;
import com.surimap.account.service.AuthSessionService;
import com.surimap.policephone.InMemoryPolicePhoneFixtureStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfig {

  @Bean
  AuthSessionService authSessionService(
      AccountLoginMapper accountLoginMapper,
      InMemoryPolicePhoneFixtureStore fixtureStore,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    return new AuthSessionService(accountLoginMapper, fixtureStore, passwordEncoder, clock);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
