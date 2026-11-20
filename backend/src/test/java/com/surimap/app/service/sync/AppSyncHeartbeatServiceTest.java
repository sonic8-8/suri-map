package com.surimap.app.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.surimap.app.service.sync.request.SyncHeartbeatServiceRequest;
import com.surimap.global.sse.ServerSentEventConnectionRegistry;
import com.surimap.global.sse.ServerSentEventMessage;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

// 저장된 이벤트가 접속 중인 상황판까지 가는지 보려고 실제 전송 worker를 켠다.
@TestPropertySource(
    properties = {
      "surimap.eventhub.dispatch.enabled=true",
      "surimap.eventhub.dispatch.polling-enabled=false"
    })
class AppSyncHeartbeatServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT = UUID.fromString("10000000-0000-0000-0000-000000005211");
  private static final UUID ACCOUNT = UUID.fromString("62000000-0000-0000-0000-000000005211");
  private static final UUID PHONE = UUID.fromString("50000000-0000-0000-0000-000000005211");
  private static final Instant BASE_TS = Instant.parse("2026-11-20T00:00:00Z");

  @Autowired private AppSyncHeartbeatService heartbeatService;
  @Autowired private ServerSentEventConnectionRegistry connectionRegistry;

  @BeforeEach
  void seed() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          account_connection_status,
          event_dispatch_job,
          incident_assignment,
          account,
          incident
        CASCADE
        """);
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES (?, 'acct-heartbeat-delivery', '{noop}fixture', 'heartbeat delivery', 'TEAM',
                'POLICE_SUBSTATION', 'ACTIVE', ?, ?)
        """,
        ACCOUNT,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, gen_random_uuid(), 'Heartbeat delivery incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT,
        Timestamp.from(BASE_TS.minusSeconds(3600)),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES ('61000000-0000-0000-0000-000000005211'::uuid, ?, ?, 'MEMBER', ?, ?, ?)
        """,
        INCIDENT,
        ACCOUNT,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  @Test
  @DisplayName("계정 heartbeat 이벤트는 전송 worker를 거쳐 접속 중인 상황판에 전달된다")
  void heartbeat_event_reaches_connected_board_and_dispatch_completes() throws Exception {
    List<ServerSentEventMessage> frames = new CopyOnWriteArrayList<>();
    try (var registration = connectionRegistry.registerForIncident(INCIDENT, frames::add)) {
      // when: 사건에 배정된 계정이 heartbeat를 보낸다.
      heartbeatService.heartbeat(
          SyncHeartbeatServiceRequest.builder().accountId(ACCOUNT).policePhoneId(PHONE).build());

      // then: 전송 작업은 완료 상태가 된다. 상황판 연결에는 이벤트가 하나 도착한다.
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(dispatchStatus()).isIn("COMPLETED", "FAILED"));
      assertThat(dispatchStatus()).isEqualTo("COMPLETED");
      assertThat(frames).hasSize(1);
      ServerSentEventMessage frame = frames.get(0);
      assertThat(frame.getEvent()).isEqualTo("ACCOUNT_HEARTBEAT_UPDATED");
      Map<String, Object> payload = frame.getData().getPayload();
      assertThat(payload)
          .containsEntry("id", ACCOUNT.toString())
          .containsEntry("accountId", ACCOUNT.toString())
          .containsEntry("status", "ONLINE")
          .containsKey("version")
          .doesNotContainKeys("policePhoneId", "sequence");
    }
  }

  private String dispatchStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT dispatch_status FROM event_dispatch_job "
            + "WHERE event_type = 'ACCOUNT_HEARTBEAT_UPDATED'",
        String.class);
  }
}
