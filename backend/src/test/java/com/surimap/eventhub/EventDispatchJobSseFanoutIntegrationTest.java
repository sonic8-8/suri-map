package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseStreamSessionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "surimap.eventhub.dispatch.enabled=true",
      "surimap.eventhub.dispatch.polling-enabled=false"
    })
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("S14P31C106-453 event_dispatch_job SSE fanout")
class EventDispatchJobSseFanoutIntegrationTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID EVENT_ID = UUID.fromString("40000000-0000-4000-8000-000000000953");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(POSTGIS_IMAGE)
          .withDatabaseName("surimap")
          .withUsername("surimap")
          .withPassword("surimap");

  @DynamicPropertySource
  static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
  }

  @Autowired private EventHub eventHub;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private SseReplayEventStore replayStore;
  @Autowired private SseStreamSessionRegistry sessionRegistry;

  @BeforeEach
  void cleanTables() {
    replayStore.clear();
    jdbcTemplate.execute("DELETE FROM event_dispatch_job");
  }

  @Test
  @DisplayName("committed event_dispatch_job is dispatched to live SSE and marked completed")
  void committedEventDispatchJobIsDispatchedToLiveSseAndMarkedCompleted() throws Exception {
    CapturingSink sink = new CapturingSink();
    AutoCloseable registration = sessionRegistry.register(INCIDENT_ID, sink);

    try {
      new TransactionTemplate(txManager).executeWithoutResult(status -> eventHub.publish(pathEvent()));

      assertThat(sink.frames()).hasSize(1);
      SseEventFrame frame = sink.frames().get(0);
      assertThat(frame.event()).isEqualTo("PATH_APPENDED");
      assertThat(frame.data().eventId()).isEqualTo(EVENT_ID);
      assertThat(frame.data().payload().get("status")).isEqualTo("RECORDING");
      assertThat(replayStore.findByEventId(EVENT_ID)).isPresent();
      assertThat(dispatchStatus()).isEqualTo("COMPLETED");
    } finally {
      registration.close();
    }
  }

  private PublishRequest pathEvent() {
    return EventStreamTestFixtures.publishRequest(
        EVENT_ID,
        INCIDENT_ID,
        "PATH_APPENDED",
        "30000000-0000-4000-8000-000000000953",
        "RECORDING",
        3L);
  }

  private String dispatchStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT dispatch_status FROM event_dispatch_job WHERE event_id = ?",
        String.class,
        EVENT_ID);
  }

  private static final class CapturingSink implements SseLiveEventSink {
    private final List<SseEventFrame> frames = new ArrayList<>();

    @Override
    public void send(SseEventFrame frame) {
      frames.add(frame);
    }

    List<SseEventFrame> frames() {
      return frames;
    }
  }
}
