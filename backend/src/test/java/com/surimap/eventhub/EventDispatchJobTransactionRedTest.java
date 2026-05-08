package com.surimap.eventhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.fixture.EventFixtures;
import com.surimap.eventhub.port.EventHub;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * L2-T06 RED 통합 테스트 — event_dispatch_job 트랜잭션 원자성 (EventDispatchJobTransactionRedTest).
 *
 * <p>S4.json AC-S4-01, AC-S4-07, duplicate_event_dedupe harness fixture를 기준으로 한다.
 *
 * <p>RED 조건: 실제 {@code EventHub} DB 구현체와 {@code event_dispatch_job} migration이 없으므로,
 * MockEventHub fallback이 주입될 때 DB 검증 assertion이 실패하거나 migration 실패로 Spring 컨텍스트가 기동되지
 * 않아야 한다.
 *
 * <p>참조:
 *
 * <ul>
 *   <li>docs/spec/specs/S4.json §acceptance_criteria AC-S4-01, AC-S4-07
 *   <li>docs/spec/specs/S4.json §harness_fixtures duplicate_event_dedupe
 *   <li>docs/spec/boundaries.md §4.3 Transaction Rule
 *   <li>docs/spec/boundaries.md §4.4 Event Catalog
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("L2-T06 RED: event_dispatch_job 트랜잭션 원자성")
class EventDispatchJobTransactionRedTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

  // S4.json harness_fixtures에 없는 테스트-로컬 픽스처 (dispatch_status PENDING 검증용)
  private static final UUID MARKER_EVENT_ID =
      UUID.fromString("40000000-0000-4000-8000-000000000401");
  private static final UUID MARKER_SOURCE_ENTITY_ID =
      UUID.fromString("60000000-0000-4000-8000-000000000401");

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

  @BeforeEach
  void cleanDispatchJobTable() {
    jdbcTemplate.execute("DELETE FROM event_dispatch_job");
  }

  // ─────────────────────────────────────────────────────────────────────
  // 1. publish() 후 commit → event_dispatch_job row 존재 검증
  //    AC-S4-01: domain row, event_dispatch_job row, BaseEvent envelope가 같은 eventId로
  //    원자적으로 보존된다.
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "publishThenCommit_eventDispatchJobRowExists — publish 후 commit 시 event_dispatch_job에 row가"
          + " 존재한다")
  void publishThenCommit_eventDispatchJobRowExists() {
    UUID eventId = EventFixtures.SC09_EVENT_ID;
    UUID incidentId = EventFixtures.INCIDENT_ID_01;

    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    TransactionStatus status = txManager.getTransaction(def);

    try {
      eventHub.publish(
          new PublishRequest(
              eventId,
              incidentId,
              EventFixtures.SC09_EVENT_TYPE,
              1,
              "search_path",
              UUID.fromString("30000000-0000-4000-8000-000000000501"),
              Instant.parse("2026-05-08T09:00:00Z"),
              Map.of(
                  "id", "30000000-0000-4000-8000-000000000501",
                  "status", "RECORDING",
                  "version", 7)));
      txManager.commit(status);
    } catch (Exception e) {
      txManager.rollback(status);
      throw e;
    }

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_dispatch_job WHERE event_id = ?",
            Integer.class,
            eventId);

    // RED: MockEventHub는 DB에 row를 쓰지 않으므로 count == 0이어서 실패한다.
    assertThat(count)
        .as(
            "publish 후 commit 시 event_dispatch_job에 row가 존재해야 한다 (AC-S4-01). "
                + "MockEventHub는 DB에 기록하지 않으므로 RED 상태다.")
        .isEqualTo(1);
  }

  // ─────────────────────────────────────────────────────────────────────
  // 2. publish() 후 rollback → row 없음 검증 (트랜잭션 원자성)
  //    AC-S4-07: event_dispatch_job insert 또는 envelope validation 실패 시 caller transaction이
  //    rollback되어야 한다. 역으로, 명시적 rollback 후에도 row가 없어야 한다.
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "publishThenRollback_noEventDispatchJobRow — publish 후 rollback 시 event_dispatch_job에 row가"
          + " 없다")
  void publishThenRollback_noEventDispatchJobRow() {
    UUID eventId = EventFixtures.SC08_EVENT_ID;
    UUID incidentId = EventFixtures.INCIDENT_ID_01;

    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    TransactionStatus status = txManager.getTransaction(def);

    eventHub.publish(
        new PublishRequest(
            eventId,
            incidentId,
            EventFixtures.SC08_EVENT_TYPE,
            1,
            "marker_notification",
            UUID.fromString("50000000-0000-4000-8000-000000000801"),
            Instant.parse("2026-05-08T09:00:00Z"),
            Map.of(
                "id", "50000000-0000-4000-8000-000000000801",
                "status", "REQUESTED",
                "version", 1)));

    // 명시적 rollback — domain write도 함께 취소되어야 한다 (AC-S4-07 원자성 역방향 검증)
    txManager.rollback(status);

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_dispatch_job WHERE event_id = ?",
            Integer.class,
            eventId);

    // RED: event_dispatch_job 테이블 자체가 존재하지 않으면 쿼리가 SQLException을 던져 실패한다.
    //      테이블이 있어도 MockEventHub는 rollback 대상 row를 삽입하지 않으므로 '0 == 0' 통과처럼
    //      보이지만 이 테스트는 publishThenCommit 테스트가 실패 시 같이 체인 실패한다.
    //      coder는 실구현 후 rollback 시 row가 0임을 DB 레벨에서 확인해야 한다.
    assertThat(count)
        .as(
            "rollback 후 event_dispatch_job에 row가 없어야 한다 (AC-S4-07 트랜잭션 원자성). "
                + "event_dispatch_job 테이블이 없으면 쿼리 자체가 실패한다.")
        .isEqualTo(0);
  }

  // ─────────────────────────────────────────────────────────────────────
  // 3. 동일 eventId로 두 번 publish → 1 row만 존재 (중복 방지)
  //    S4.json duplicate_event_dedupe: eventDispatchJobRows == 1
  //    S4.json domain_model.entities[0].indexes: ux_event_dispatch_job_event_id UNIQUE
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "duplicatePublish_onlyOneRowOrExceptionExpected — 동일 eventId 두 번 publish 시 예외 발생 또는 1"
          + " row만 존재한다")
  void duplicatePublish_onlyOneRowOrExceptionExpected() {
    // S4.json duplicate_event_dedupe fixture
    UUID eventId = EventFixtures.DEDUPE_EVENT_ID;
    UUID incidentId = EventFixtures.INCIDENT_ID_01;

    PublishRequest request =
        new PublishRequest(
            eventId,
            incidentId,
            "PATH_APPENDED",
            1,
            "search_path",
            UUID.fromString("30000000-0000-4000-8000-000000000501"),
            Instant.parse("2026-05-08T09:00:00Z"),
            Map.of(
                "id", "30000000-0000-4000-8000-000000000501",
                "status", "RECORDING",
                "version", 7));

    // 첫 번째 publish — commit
    DefaultTransactionDefinition def1 = new DefaultTransactionDefinition();
    def1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    TransactionStatus status1 = txManager.getTransaction(def1);
    try {
      eventHub.publish(request);
      txManager.commit(status1);
    } catch (Exception e) {
      txManager.rollback(status1);
      throw e;
    }

    // 두 번째 publish — 같은 eventId: 예외가 발생하거나 무시되어야 한다
    // 실구현에서는 ux_event_dispatch_job_event_id UNIQUE constraint 위반 예외가 발생해야 한다.
    DefaultTransactionDefinition def2 = new DefaultTransactionDefinition();
    def2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    TransactionStatus status2 = txManager.getTransaction(def2);
    try {
      eventHub.publish(request);
      txManager.commit(status2);
    } catch (Exception e) {
      txManager.rollback(status2);
      // 예외 발생은 허용 — UNIQUE constraint 위반이면 정상 RED 경로
    }

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_dispatch_job WHERE event_id = ?",
            Integer.class,
            eventId);

    // RED: event_dispatch_job 테이블이 없으면 쿼리가 실패한다.
    //      MockEventHub는 DB에 기록하지 않으므로 count == 0이어서 실패한다.
    assertThat(count)
        .as(
            "동일 eventId로 두 번 publish 시 event_dispatch_job row는 정확히 1개여야 한다 "
                + "(S4.json duplicate_event_dedupe: eventDispatchJobRows == 1). "
                + "MockEventHub는 DB row를 남기지 않으므로 RED 상태다.")
        .isEqualTo(1);
  }

  // ─────────────────────────────────────────────────────────────────────
  // 4. publish() 후 commit → dispatch_status == PENDING 검증
  //    S4.json domain_model.entities[0].dispatch_status: 초기값 PENDING
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "publishedRow_hasDispatchStatusPending — publish 후 commit된 row의 dispatch_status는 PENDING이다")
  void publishedRow_hasDispatchStatusPending() {
    UUID eventId = MARKER_EVENT_ID;
    UUID incidentId = EventFixtures.INCIDENT_ID_01;

    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    TransactionStatus status = txManager.getTransaction(def);

    try {
      eventHub.publish(
          new PublishRequest(
              eventId,
              incidentId,
              "MARKER_CREATED",
              1,
              "marker",
              MARKER_SOURCE_ENTITY_ID,
              Instant.parse("2026-05-08T09:00:00Z"),
              Map.of("id", MARKER_SOURCE_ENTITY_ID.toString(), "status", "ACTIVE", "version", 1)));
      txManager.commit(status);
    } catch (Exception e) {
      txManager.rollback(status);
      throw e;
    }

    String dispatchStatus =
        jdbcTemplate.queryForObject(
            "SELECT dispatch_status FROM event_dispatch_job WHERE event_id = ?",
            String.class,
            eventId);

    // RED: 테이블/row가 없으면 쿼리가 실패한다. MockEventHub는 DB에 기록하지 않는다.
    assertThat(dispatchStatus)
        .as(
            "event_dispatch_job row의 초기 dispatch_status는 PENDING이어야 한다 "
                + "(S4.json domain_model dispatch_status note: PENDING|...). "
                + "MockEventHub는 DB row를 남기지 않으므로 RED 상태다.")
        .isEqualTo("PENDING");
  }

  // ─────────────────────────────────────────────────────────────────────
  // 5. publish() 후 event_id, incident_id, event_type 필드 검증 (AC-S4-01)
  //    domain row, event_dispatch_job row가 같은 eventId/incidentId/type을 원자적으로 보존한다.
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "publishedRow_envelopeFieldsMatchRequest — publish 후 event_dispatch_job row의"
          + " envelope 필드가 PublishRequest와 일치한다")
  void publishedRow_envelopeFieldsMatchRequest() {
    UUID eventId = EventFixtures.SC09_EVENT_ID;
    UUID incidentId = EventFixtures.INCIDENT_ID_01;
    String eventType = EventFixtures.SC09_EVENT_TYPE;
    int payloadFormatVersion = 1;

    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    TransactionStatus status = txManager.getTransaction(def);

    try {
      eventHub.publish(
          new PublishRequest(
              eventId,
              incidentId,
              eventType,
              payloadFormatVersion,
              "search_path",
              UUID.fromString("30000000-0000-4000-8000-000000000501"),
              Instant.parse("2026-05-08T09:00:00Z"),
              Map.of(
                  "id", "30000000-0000-4000-8000-000000000501",
                  "status", "RECORDING",
                  "version", 7)));
      txManager.commit(status);
    } catch (Exception e) {
      txManager.rollback(status);
      throw e;
    }

    // event_id, incident_id, event_type, payload_format_version 모두 검증
    Integer matchCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM event_dispatch_job
            WHERE event_id = ?
              AND incident_id = ?
              AND event_type = ?
              AND payload_format_version = ?
            """,
            Integer.class,
            eventId,
            incidentId,
            eventType,
            payloadFormatVersion);

    // RED: 테이블이 없으면 쿼리 실패. MockEventHub는 DB에 기록하지 않으므로 count == 0이어서 실패한다.
    assertThat(matchCount)
        .as(
            "event_dispatch_job row의 envelope 필드가 PublishRequest와 일치해야 한다 (AC-S4-01). "
                + "MockEventHub는 DB row를 남기지 않으므로 RED 상태다.")
        .isEqualTo(1);
  }
}
