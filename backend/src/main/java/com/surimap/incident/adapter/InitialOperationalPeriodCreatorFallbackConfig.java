package com.surimap.incident.adapter;

import com.surimap.operationalperiod.command.InitialOperationalPeriodCreator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InitialOperationalPeriodCreatorFallbackConfig {

  // S8 production bean이 들어오면 이 bean은 등록되지 않는다.
  @Bean
  @ConditionalOnMissingBean(InitialOperationalPeriodCreator.class)
  InitialOperationalPeriodCreator blockingInitialOperationalPeriodCreator() {
    return new BlockingInitialOperationalPeriodCreator();
  }
}
