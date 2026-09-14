package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.eventhub.stream.SseEventFrame;
import com.surimap.eventhub.stream.SseLiveEventSink;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.eventhub.stream.SseStreamSessionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  @Autowired private SseStreamService streamService;

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
      new TransactionTemplate(txManager)
          .executeWithoutResult(status -> eventHub.publish(pathEvent()));

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

  @Test
  @DisplayName("닫힌 SSE 연결이 남아 있어도, 발견 알림을 정상 연결에 전송하고 작업을 완료한다")
  void dispatch_when_closed_connection_remains_sends_person_found_and_completes_job()
      throws Exception {
    // given: 종료 콜백이 처리되기 전에 새 이벤트가 들어오는 상황이다.
    var disconnected = streamService.openStream(INCIDENT_ID, null);
    disconnected.completeWithError(new IllegalStateException("response already unusable"));
    var connected = new CapturingSink();
    try (var registration = sessionRegistry.register(INCIDENT_ID, connected)) {
      // when: 실제 트랜잭션으로 발견 알림을 저장하고 커밋 후 전송한다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(status -> eventHub.publish(createPersonFoundEvent()));

      // then: 정상 연결은 수신하고, 이벤트는 재전송할 수 있도록 저장돼 있다.
      assertThat(connected.frames()).hasSize(1);
      assertThat(connected.frames().get(0).event()).isEqualTo("PERSON_FOUND");
      assertThat(connected.frames().get(0).data().eventId()).isEqualTo(EVENT_ID);
      assertThat(sessionRegistry.sinks(INCIDENT_ID)).containsExactly(connected);
      assertThat(replayStore.findByEventId(EVENT_ID)).isPresent();
      // COMPLETED는 서버 전송 처리의 완료이며 브라우저 표시 확인이 아니다.
      assertThat(dispatchStatus()).isEqualTo("COMPLETED");
    } finally {
      sessionRegistry.release(INCIDENT_ID);
    }
  }

  @Test
  @DisplayName("재전송 저장에 실패하면, 알림을 전송하거나 작업을 완료 처리하지 않는다")
  void dispatch_when_replay_storage_rejects_event_marks_job_failed() throws Exception {
    // given: 파기된 사건은 재전송 저장소에 새 이벤트를 저장할 수 없다.
    replayStore.purgeIncident(INCIDENT_ID);
    var connected = new CapturingSink();
    try (var registration = sessionRegistry.register(INCIDENT_ID, connected)) {
      // when: 저장이 거부될 발견 알림의 전송 작업을 처리한다.
      new TransactionTemplate(txManager)
          .executeWithoutResult(status -> eventHub.publish(createPersonFoundEvent()));

      // then: 연결 하나의 실패와 달리 저장 실패는 전송 작업의 실패로 남는다.
      assertThat(connected.frames()).isEmpty();
      assertThat(replayStore.findByEventId(EVENT_ID)).isEmpty();
      assertThat(dispatchStatus()).isEqualTo("FAILED");
    }
  }

  private PublishRequest createPersonFoundEvent() {
    var markerId = UUID.fromString("30000000-0000-4000-8000-000000000953");
    return new PublishRequest(
        EVENT_ID,
        INCIDENT_ID,
        "PERSON_FOUND",
        1,
        "marker",
        markerId,
        EventStreamTestFixtures.CREATED_AT,
        Map.of("id", markerId.toString(), "status", "ACTIVE", "version", 1));
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
