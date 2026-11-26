package com.surimap.app.controller.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.sse.ServerSentEventConnectionRegistry;
import com.surimap.global.sse.ServerSentEventMessage;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(addFilters = false)
@Import(SyncHeartbeatApiTest.MutableClockConfig.class)
class SyncHeartbeatApiTest extends PostGisIntegrationTestSupport {

  private static final String INCIDENT_A = "10000000-0000-0000-0000-000000005201";
  private static final String INCIDENT_B = "10000000-0000-0000-0000-000000005202";
  private static final String INCIDENT_CLOSED = "10000000-0000-0000-0000-000000005203";
  private static final String ACCOUNT_A = "62000000-0000-0000-0000-000000005201";
  private static final String ACCOUNT_B = "62000000-0000-0000-0000-000000005202";
  private static final String FIELD_COMMANDER_TEAM = "62000000-0000-0000-0000-000000005203";
  private static final String MULTI_INCIDENT_ACCOUNT = "62000000-0000-0000-0000-000000005204";
  private static final String UNASSIGNED_ACCOUNT = "62000000-0000-0000-0000-000000005205";
  private static final String COMMANDER = "63000000-0000-0000-0000-000000005201";
  // 서버에는 업무폰마다 두는 기록이 없으므로, 두 업무폰 ID는 아무 UUID다.
  private static final String PHONE_P = "50000000-0000-0000-0000-000000005201";
  private static final String PHONE_Q = "50000000-0000-0000-0000-000000005202";
  private static final Instant BASE_TS = Instant.parse("2026-11-20T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private MutableClock clock;
  @Autowired private ServerSentEventConnectionRegistry connectionRegistry;

  @BeforeEach
  void seed() {
    clock.set(BASE_TS);
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          account_connection_status,
          event_dispatch_job,
          location_data_access_audit,
          incident_assignment,
          account,
          incident
        CASCADE
        """);
    insertAccount(ACCOUNT_A, "TEAM");
    insertAccount(ACCOUNT_B, "TEAM");
    insertAccount(FIELD_COMMANDER_TEAM, "TEAM");
    insertAccount(MULTI_INCIDENT_ACCOUNT, "PATROL_CAR");
    insertAccount(UNASSIGNED_ACCOUNT, "TEAM");
    insertAccount(COMMANDER, "COMMAND");
    insertIncident(INCIDENT_A, "OPEN");
    insertIncident(INCIDENT_B, "OPEN");
    insertIncident(INCIDENT_CLOSED, "CLOSED");
    assign("5201", INCIDENT_A, ACCOUNT_A, "MEMBER");
    assign("5202", INCIDENT_B, ACCOUNT_B, "MEMBER");
    assign("5203", INCIDENT_A, FIELD_COMMANDER_TEAM, "FIELD_COMMANDER");
    assign("5204", INCIDENT_A, COMMANDER, "INCIDENT_COMMANDER");
    assign("5205", INCIDENT_A, MULTI_INCIDENT_ACCOUNT, "MEMBER");
    assign("5206", INCIDENT_B, MULTI_INCIDENT_ACCOUNT, "MEMBER");
    assign("5207", INCIDENT_CLOSED, MULTI_INCIDENT_ACCOUNT, "MEMBER");
  }

  @Test
  @DisplayName("DB에 기록이 없는 업무폰 ID로 heartbeat를 보내면 성공하고 응답에는 계정 ID가 있고 업무폰 ID와 순번은 없다")
  void heartbeat_from_unknown_police_phone_succeeds_with_account_id() throws Exception {
    // when & then: 처음 쓰는 업무폰에서 계정 A로 heartbeat를 보낸다.
    heartbeat(ACCOUNT_A, PHONE_P, "2026-11-20T08:59:30+09:00")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("ONLINE")))
        .andExpect(jsonPath("$.accountId", is(ACCOUNT_A)))
        .andExpect(jsonPath("$.lastHeartbeatAt", is("2026-11-20T00:00:00Z")))
        .andExpect(jsonPath("$.lastSyncAt", is("2026-11-19T23:59:30Z")))
        .andExpect(jsonPath("$.policePhoneId").doesNotExist())
        .andExpect(jsonPath("$.sequence").doesNotExist());
  }

  @Test
  @DisplayName("업무폰 ID 헤더가 없거나 UUID 형식이 아니면 police_phone_required로 거절된다")
  void heartbeat_without_uuid_police_phone_is_rejected() throws Exception {
    // when & then: 업무폰 ID 없이 보낸다.
    heartbeat(ACCOUNT_A, null, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));
    // when & then: UUID 형식이 아닌 업무폰 ID로 보낸다.
    heartbeat(ACCOUNT_A, "1-1-1-1-1", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("police_phone_required")));

    assertThat(connectionStatusCount()).isZero();
  }

  @Test
  @DisplayName("사건에 배정되지 않은 계정의 heartbeat는 성공하고 기록되며 어느 상황판에도 이벤트를 보내지 않는다")
  void unassigned_account_heartbeat_succeeds_without_event() throws Exception {
    // given: 진행 중인 사건 A에 상황판이 연결돼 있다.
    List<ServerSentEventMessage> received = new CopyOnWriteArrayList<>();
    try (var registration =
        connectionRegistry.registerForIncident(UUID.fromString(INCIDENT_A), received::add)) {
      // when & then: 배정이 없는 계정으로 heartbeat를 보낸다.
      heartbeat(UNASSIGNED_ACCOUNT, PHONE_P, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accountId", is(UNASSIGNED_ACCOUNT)));
    }

    // then: 통신 상태 기록은 남는다. heartbeat 이벤트는 보내지도 저장되지도 않는다.
    assertThat(connectionStatusCount()).isEqualTo(1);
    assertThat(received).isEmpty();
    assertThat(heartbeatEvents()).isEmpty();
  }

  @Test
  @DisplayName("업무폰 P에서 계정 A로 heartbeat를 보낸 뒤 계정 B로 보내면 B가 배정된 사건의 통신 상태에 B가 정상으로 나온다")
  void heartbeat_from_account_b_on_phone_used_by_a_shows_b_online() throws Exception {
    // given: 업무폰 P를 계정 A가 쓰다가 계정 B가 넘겨받는다.
    heartbeat(ACCOUNT_A, PHONE_P, null).andExpect(status().isOk());
    clock.set(BASE_TS.plusSeconds(120));
    heartbeat(ACCOUNT_B, PHONE_P, null).andExpect(status().isOk());

    // when & then: 사건 B의 상황판 통신 상태에서 계정 B는 정상이다.
    clock.set(BASE_TS.plusSeconds(130));
    connectionStatus(INCIDENT_B)
        .andExpect(status().isOk())
        .andExpect(jsonPath(rowField(ACCOUNT_B, "accountId"), contains(ACCOUNT_B)))
        .andExpect(jsonPath(rowField(ACCOUNT_B, "freshnessStatus"), contains("ONLINE")))
        .andExpect(
            jsonPath(rowField(ACCOUNT_B, "lastHeartbeatAt"), contains("2026-11-20T00:02:00Z")));
  }

  @Test
  @DisplayName("같은 계정으로 다른 업무폰에서 heartbeat를 보내도 마지막 heartbeat 시각이 갱신된다")
  void same_account_on_another_police_phone_updates_last_heartbeat_at() throws Exception {
    // given: 계정 A가 업무폰 P에서 보낸 뒤 업무폰 Q에서 다시 보낸다.
    heartbeat(ACCOUNT_A, PHONE_P, null).andExpect(status().isOk());
    clock.set(BASE_TS.plusSeconds(30));
    heartbeat(ACCOUNT_A, PHONE_Q, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastHeartbeatAt", is("2026-11-20T00:00:30Z")));

    // when & then: 사건 A의 상황판에는 나중 시각이 나온다.
    connectionStatus(INCIDENT_A)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(rowField(ACCOUNT_A, "lastHeartbeatAt"), contains("2026-11-20T00:00:30Z")));
  }

  @Test
  @DisplayName("통신 상태 행의 공통 필드 sequence에는 heartbeat 순번이 아니라 기록 version이 담긴다")
  void connection_status_row_sequence_equals_record_version() throws Exception {
    // given: 계정 A가 heartbeat를 두 번 보내 기록 version이 2가 된다.
    heartbeat(ACCOUNT_A, PHONE_P, null).andExpect(status().isOk());
    clock.set(BASE_TS.plusSeconds(30));
    heartbeat(ACCOUNT_A, PHONE_P, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version", is(2)));

    // when & then: 사건 A의 통신 상태에서 계정 A 행의 sequence는 version과 같다.
    // heartbeat를 보내지 않은 계정은 기록이 없어 version과 sequence가 모두 0이다.
    connectionStatus(INCIDENT_A)
        .andExpect(status().isOk())
        .andExpect(jsonPath(rowField(ACCOUNT_A, "version"), contains(2)))
        .andExpect(jsonPath(rowField(ACCOUNT_A, "sequence"), contains(2)))
        .andExpect(jsonPath(rowField(FIELD_COMMANDER_TEAM, "version"), contains(0)))
        .andExpect(jsonPath(rowField(FIELD_COMMANDER_TEAM, "sequence"), contains(0)));
  }

  @Test
  @DisplayName("통신 상태에는 배정된 현장 계정만 한 행씩 나오고 heartbeat를 보내지 않은 계정은 끊김이며 업무폰 ID와 heartbeat 순번이 없다")
  void connection_status_lists_assigned_field_accounts_only() throws Exception {
    // when & then: heartbeat를 아무도 보내지 않은 사건 A의 통신 상태를 조회한다.
    connectionStatus(INCIDENT_A)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.slots.connection_status[*].id",
                containsInAnyOrder(ACCOUNT_A, FIELD_COMMANDER_TEAM, MULTI_INCIDENT_ACCOUNT)))
        .andExpect(jsonPath("$.slots.connection_status[*].freshnessStatus", everyItem(is("LOST"))))
        .andExpect(jsonPath("$.slots.connection_status[*]", everyItem(not(hasKey("policePhoneId")))))
        .andExpect(
            jsonPath("$.slots.connection_status[*]", everyItem(not(hasKey("heartbeatSequence")))))
        .andExpect(jsonPath("$.slots.police_phone_freshness").doesNotExist());
  }

  @Test
  @DisplayName("두 진행 중 사건에 배정된 계정의 heartbeat는 두 사건의 상황판에 이벤트를 하나씩 보내고 종료된 사건에는 보내지 않으며 이벤트 발송 작업으로 저장하지 않는다")
  void heartbeat_sends_one_event_to_each_open_assigned_incident_board() throws Exception {
    // given: 사건 A, B와 종료된 사건에 상황판이 하나씩 연결돼 있다.
    Map<String, List<ServerSentEventMessage>> received = new HashMap<>();
    List<AutoCloseable> registrations = new ArrayList<>();
    for (String incidentId : List.of(INCIDENT_A, INCIDENT_B, INCIDENT_CLOSED)) {
      List<ServerSentEventMessage> messages = new CopyOnWriteArrayList<>();
      received.put(incidentId, messages);
      registrations.add(
          connectionRegistry.registerForIncident(UUID.fromString(incidentId), messages::add));
    }
    try {
      // when: 세 사건에 배정된 계정이 heartbeat를 보낸다.
      heartbeat(MULTI_INCIDENT_ACCOUNT, PHONE_P, "2026-11-20T08:59:30+09:00")
          .andExpect(status().isOk());
    } finally {
      for (AutoCloseable registration : registrations) {
        registration.close();
      }
    }

    // then: 진행 중인 두 사건의 상황판만 이벤트를 하나씩 받는다. 사건마다 이벤트 ID가 다르다.
    assertThat(received.get(INCIDENT_A)).hasSize(1);
    assertThat(received.get(INCIDENT_B)).hasSize(1);
    assertThat(received.get(INCIDENT_CLOSED)).isEmpty();
    assertThat(received.get(INCIDENT_A).get(0).getData().getEventId())
        .isNotEqualTo(received.get(INCIDENT_B).get(0).getData().getEventId());
    for (String incidentId : List.of(INCIDENT_A, INCIDENT_B)) {
      ServerSentEventMessage message = received.get(incidentId).get(0);
      assertThat(message.isSequenced()).isFalse();
      assertThat(message.getEvent()).isEqualTo("ACCOUNT_HEARTBEAT_UPDATED");
      assertThat(message.getData().getIncidentId()).isEqualTo(UUID.fromString(incidentId));
      assertThat(message.getData().getSourceEntityType()).isEqualTo("account_heartbeat");
      assertThat(message.getData().getSourceEntityId())
          .isEqualTo(UUID.fromString(MULTI_INCIDENT_ACCOUNT));
      Map<String, Object> payload = message.getData().getPayload();
      assertThat(payload)
          .containsEntry("id", MULTI_INCIDENT_ACCOUNT)
          .containsEntry("status", "ONLINE")
          .containsEntry("version", 1L)
          .containsEntry("accountId", MULTI_INCIDENT_ACCOUNT)
          .containsEntry("lastHeartbeatAt", BASE_TS.toString())
          .containsEntry("lastSyncAt", "2026-11-19T23:59:30Z")
          .doesNotContainKeys("policePhoneId", "sequence");
    }
    // then: 이벤트 발송 작업으로는 저장되지 않는다.
    assertThat(heartbeatEvents()).isEmpty();
  }

  @Test
  @DisplayName("마지막 동기화 시각은 더 늦은 값만 반영된다")
  void last_sync_at_keeps_later_value() throws Exception {
    // given: 늦은 동기화 시각을 먼저 보낸다.
    heartbeat(ACCOUNT_A, PHONE_P, "2026-11-20T09:00:00+09:00").andExpect(status().isOk());
    clock.set(BASE_TS.plusSeconds(30));

    // when & then: 더 이른 동기화 시각을 보내도 늦은 값이 남는다.
    heartbeat(ACCOUNT_A, PHONE_P, "2026-11-20T08:50:00+09:00")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastHeartbeatAt", is("2026-11-20T00:00:30Z")))
        .andExpect(jsonPath("$.lastSyncAt", is("2026-11-20T00:00:00Z")));
  }

  private ResultActions heartbeat(String accountId, String policePhoneId, String lastSyncAt)
      throws Exception {
    String lastSyncField = lastSyncAt == null ? "" : ", \"lastSyncAt\": \"" + lastSyncAt + "\"";
    return performAs(
        appAccount(accountId, policePhoneId),
        post("/api/sync/heartbeat")
            .contentType("application/json")
            .content("{\"clientTs\": \"2026-11-20T09:00:00+09:00\"" + lastSyncField + "}"));
  }

  private ResultActions connectionStatus(String incidentId) throws Exception {
    return performAs(
        commander(),
        get("/api/incidents/{incidentId}/board", incidentId)
            .param("includeSlots", "connection_status"));
  }

  private static String rowField(String accountId, String field) {
    return "$.slots.connection_status[?(@.id == '" + accountId + "')]." + field;
  }

  // 한 테스트에서 앱 계정과 지휘 계정을 번갈아 쓰므로, 요청마다 인증 정보를 직접 넣는다.
  private ResultActions performAs(
      SuriMapAuthentication auth, MockHttpServletRequestBuilder request) throws Exception {
    SecurityContextHolder.getContext().setAuthentication(auth);
    try {
      return mockMvc.perform(request);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static SuriMapAuthentication appAccount(String accountId, String policePhoneId) {
    return new SuriMapAuthentication(
        accountId,
        AccountType.TEAM,
        OrganizationType.POLICE_SUBSTATION,
        Channel.APP,
        policePhoneId,
        List.of(new SimpleGrantedAuthority(Role.MEMBER.name())));
  }

  private static SuriMapAuthentication commander() {
    return new SuriMapAuthentication(
        COMMANDER,
        AccountType.COMMAND,
        OrganizationType.POLICE_SUBSTATION,
        Channel.WEB,
        null,
        List.of(new SimpleGrantedAuthority(Role.FIELD_COMMANDER.name())));
  }

  private Integer connectionStatusCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM account_connection_status", Integer.class);
  }

  private List<Map<String, Object>> heartbeatEvents() {
    return jdbcTemplate.queryForList(
        """
        SELECT event_id::text AS event_id, incident_id::text AS incident_id,
               source_entity_type, source_entity_id::text AS source_entity_id, payload::text AS payload
        FROM event_dispatch_job
        WHERE event_type = 'ACCOUNT_HEARTBEAT_UPDATED'
        """);
  }

  private void insertAccount(String accountId, String accountType) {
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?, '{noop}fixture', ?, ?, 'POLICE_SUBSTATION', 'ACTIVE', ?, ?)
        """,
        accountId,
        "acct-" + accountId,
        "account " + accountId,
        accountType,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertIncident(String incidentId, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, closed_at, version, created_at, updated_at
        )
        VALUES (?::uuid, gen_random_uuid(), 'Connection status incident', ?, ?, ?, 1, ?, ?)
        """,
        incidentId,
        status,
        Timestamp.from(BASE_TS.minusSeconds(3600)),
        "CLOSED".equals(status) ? Timestamp.from(BASE_TS.minusSeconds(60)) : null,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void assign(String suffix, String incidentId, String accountId, String incidentRole) {
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?)
        """,
        "61000000-0000-0000-0000-00000000" + suffix,
        incidentId,
        accountId,
        incidentRole,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  static final class MutableClock extends Clock {

    private volatile Instant now = BASE_TS;

    void set(Instant instant) {
      now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class MutableClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }
}
