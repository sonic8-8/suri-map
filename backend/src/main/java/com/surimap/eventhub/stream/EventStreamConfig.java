package com.surimap.eventhub.stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(EventStreamExceptionHandler.class)
public class EventStreamConfig {

  @Bean
  SseReplayEventStore sseReplayEventStore() {
    return new InMemorySseReplayEventStore();
  }

  @Bean
  SseReplayService sseReplayService(SseReplayEventStore sseReplayEventStore) {
    return new SseReplayService(sseReplayEventStore);
  }

  @Bean
  SseStreamSessionRegistry sseStreamSessionRegistry() {
    return new SseStreamSessionRegistry();
  }

  @Bean
  SseStreamService sseStreamService(
      SseReplayService sseReplayService,
      SseReplayEventStore sseReplayEventStore,
      SseStreamSessionRegistry sseStreamSessionRegistry) {
    return new SseStreamService(sseReplayService, sseReplayEventStore, sseStreamSessionRegistry);
  }
}
