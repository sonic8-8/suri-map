package com.surimap.api.controller.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.support.auth.WithMockAccount;
import jakarta.servlet.ServletException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
class SearchPathQueryApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_A = UUID.fromString("10000000-0000-0000-0000-000000004501");
  private static final UUID INCIDENT_B = UUID.fromString("10000000-0000-0000-0000-000000004502");
  private static final UUID OP_A = UUID.fromString("70000000-0000-0000-0000-000000004501");
  private static final UUID OP_B = UUID.fromString("70000000-0000-0000-0000-000000004502");
  private static final String APP_A = "62000000-0000-0000-0000-000000004501";
  private static final String APP_A_PEER = "62000000-0000-0000-0000-000000004502";
  private static final String APP_B = "62000000-0000-0000-0000-000000004503";
  private static final String COMMANDER = "63000000-0000-0000-0000-000000004501";
  private static final String APP_A_PHONE = "50000000-0000-0000-0000-000000004501";
  private static final Instant BASE_TS = Instant.parse("2026-11-16T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void seedIncidents() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          location_data_access_audit,
          search_path_lifecycle_event,
          search_path_excluded_point,
          search_path_gps_point,
          search_path_segment,
          search_path,
          duty_shift,
          operational_period,
          incident_assignment,
          police_phone,
          account,
          incident
        CASCADE
        """);
    insertAccount(APP_A, "TEAM", "MISSING_TEAM");
    insertAccount(APP_A_PEER, "PATROL_CAR", "POLICE_SUBSTATION");
    insertAccount(APP_B, "TEAM", "MISSING_TEAM");
    insertAccount(COMMANDER, "COMMAND", "POLICE_SUBSTATION");
    // 사건 A에는 지휘 계정과 같은 조직 유형의 계정이 배정돼 있고, 사건 B에는 없다.
    insertIncidentWithOp(INCIDENT_A, OP_A);
    insertIncidentWithOp(INCIDENT_B, OP_B);
    insertAssignedPath(INCIDENT_A, OP_A, APP_A, "4501");
    insertAssignedPath(INCIDENT_A, OP_A, APP_A_PEER, "4502");
    insertAssignedPath(INCIDENT_B, OP_B, APP_B, "4503");
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("A 사건에 배정된 앱 계정이 B 사건의 수색 경로를 요청하면 incident_access_denied로 거절하고 조회 기록을 남기지 않는다")
  void app_account_assigned_to_other_incident_is_denied() throws Exception {
    // when & then: 배정되지 않은 사건의 수색 경로를 요청한다.
    mockMvc
        .perform(get("/api/search-paths").param("incidentId", INCIDENT_B.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")))
        .andExpect(jsonPath("$.paths").doesNotExist());

    assertThat(accessAuditCount()).isZero();
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB)
  @DisplayName("지휘 계정이 조직 범위 밖의 사건을 웹으로 요청하면 incident_access_denied로 거절한다")
  void command_account_outside_organization_scope_is_denied() throws Exception {
    // when & then: 같은 조직 유형의 배정이 없는 사건 B를 요청한다.
    mockMvc
        .perform(get("/api/search-paths").param("incidentId", INCIDENT_B.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")))
        .andExpect(jsonPath("$.paths").doesNotExist());

    assertThat(accessAuditCount()).isZero();
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("배정된 앱 계정은 자기 사건에서 다른 계정의 수색 경로도 함께 받고 조회 기록이 한 행 남는다")
  void assigned_app_account_receives_paths_of_other_accounts_and_records_access()
      throws Exception {
    // when & then: 자기 사건의 수색 경로를 요청한다.
    mockMvc
        .perform(
            get("/api/search-paths")
                .param("incidentId", INCIDENT_A.toString())
                .param("opId", OP_A.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths.length()", is(2)))
        .andExpect(jsonPath("$.paths[*].accountId", containsInAnyOrder(APP_A, APP_A_PEER)))
        .andExpect(jsonPath("$.paths[*].incidentId", containsInAnyOrder(
            INCIDENT_A.toString(), INCIDENT_A.toString())))
        .andExpect(jsonPath("$.paths[0].geometry.type", is("LineString")));

    // then: 조회한 계정, 사건, 채널, 업무폰, 목적이 위치 조회 기록에 한 행 남는다.
    assertThat(accessAuditCount()).isEqualTo(1);
    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            """
            SELECT incident_id::text AS incident_id, account_id::text AS account_id,
                   police_phone_id::text AS police_phone_id, access_channel, access_purpose
            FROM location_data_access_audit
            """);
    assertThat(audit)
        .containsEntry("incident_id", INCIDENT_A.toString())
        .containsEntry("account_id", APP_A)
        .containsEntry("police_phone_id", APP_A_PHONE)
        .containsEntry("access_channel", "APP")
        .containsEntry("access_purpose", "SEARCH_PATH_READ");
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = "not-a-uuid-phone")
  @DisplayName("배정된 앱 계정이 UUID가 아닌 업무폰 헤더로 요청해도 200을 받고 업무폰이 비어 있는 위치 조회 기록이 한 행 남는다")
  void assigned_app_account_with_non_uuid_police_phone_receives_paths_and_records_access()
      throws Exception {
    // when & then: 업무폰 헤더 값이 UUID 형식이 아닌 채로 자기 사건의 수색 경로를 요청한다.
    mockMvc
        .perform(get("/api/search-paths").param("incidentId", INCIDENT_A.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths[*].accountId", containsInAnyOrder(APP_A, APP_A_PEER)));

    // then: 위치 조회 기록에는 업무폰이 빈 채로 계정과 목적이 남는다.
    assertThat(accessAuditCount()).isEqualTo(1);
    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            """
            SELECT account_id::text AS account_id, police_phone_id, access_purpose
            FROM location_data_access_audit
            """);
    assertThat(audit)
        .containsEntry("account_id", APP_A)
        .containsEntry("police_phone_id", null)
        .containsEntry("access_purpose", "SEARCH_PATH_READ");
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB)
  @DisplayName("지휘 계정은 개인 배정이 없어도 조직 범위 안의 사건 수색 경로를 웹으로 받는다")
  void command_account_in_organization_scope_receives_paths_without_personal_assignment()
      throws Exception {
    // when & then: 같은 조직 유형의 계정이 배정된 사건 A를 요청한다.
    mockMvc
        .perform(get("/api/search-paths").param("incidentId", INCIDENT_A.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths[*].accountId", containsInAnyOrder(APP_A, APP_A_PEER)));

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM location_data_access_audit
                WHERE account_id = ?::uuid AND access_channel = 'WEB'
                  AND access_purpose = 'SEARCH_PATH_READ'
                """,
                Integer.class,
                COMMANDER))
        .isEqualTo(1);
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("종료된 사건도 배정된 앱 계정은 지금과 같이 수색 경로를 받는다")
  void assigned_app_account_receives_paths_of_closed_incident() throws Exception {
    // given: 사건 A가 종료됐다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = ? WHERE id = ?::uuid",
        Timestamp.from(BASE_TS.plusSeconds(3600)),
        INCIDENT_A.toString());

    // when & then: 사건 권한은 배정으로 정해지므로 앱 계정은 종료 여부와 관계없이 수색 경로를 받는다.
    mockMvc
        .perform(get("/api/search-paths").param("incidentId", INCIDENT_A.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths.length()", is(2)));
  }

  @Test
  @WithMockAccount(accountId = APP_A, policePhoneId = APP_A_PHONE)
  @DisplayName("위치 조회 기록 저장이 실패하면 수색 경로 응답이 나가지 않는다")
  void access_audit_failure_blocks_search_path_response() throws Exception {
    // given: 위치 조회 기록 테이블에 수색 경로 조회 목적의 행을 거절하는 제약을 추가한다.
    jdbcTemplate.execute(
        """
        ALTER TABLE location_data_access_audit
          ADD CONSTRAINT chk_test_reject_search_path_read
          CHECK (access_purpose <> 'SEARCH_PATH_READ')
        """);
    try {
      // 처리되지 않은 예외에는 MockMvc 응답 상태가 없으므로, 응답 대신 예외가 전파되는 것을 확인한다(운영 서버에서는 500이 나간다).
      // when & then: 기록 저장 오류가 요청 처리 밖으로 전달돼 수색 경로 응답이 만들어지지 않는다.
      assertThatThrownBy(
              () ->
                  mockMvc.perform(
                      get("/api/search-paths").param("incidentId", INCIDENT_A.toString())))
          .isInstanceOf(ServletException.class)
          .hasStackTraceContaining("chk_test_reject_search_path_read");
      assertThat(accessAuditCount()).isZero();
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE location_data_access_audit DROP CONSTRAINT chk_test_reject_search_path_read");
    }
  }

  private Integer accessAuditCount() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM location_data_access_audit", Integer.class);
  }

  private void insertAccount(String accountId, String accountType, String organizationType) {
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?, '{noop}fixture', ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        accountId,
        "acct-" + accountId,
        "account " + accountId,
        accountType,
        organizationType,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertIncidentWithOp(UUID incidentId, UUID opId) {
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?::uuid, gen_random_uuid(), 'Search path access incident', 'OPEN', ?, 1, ?, ?)
        """,
        incidentId.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_by_account_id,
          started_at, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 1, 'ACTIVE', 'INITIAL_IMPORT', ?::uuid, ?, 1, ?, ?)
        """,
        opId.toString(),
        incidentId.toString(),
        COMMANDER,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  // 계정을 사건에 배정하고, 그 계정의 업무폰, 근무, 수색 경로를 한 개씩 넣는다.
  private void insertAssignedPath(UUID incidentId, UUID opId, String accountId, String suffix) {
    String assignmentId = "61000000-0000-0000-0000-00000000" + suffix;
    String policePhoneId = "50000000-0000-0000-0000-00000000" + suffix;
    String dutyShiftId = "60000000-0000-0000-0000-00000000" + suffix;
    String pathId = "81000000-0000-0000-0000-00000000" + suffix;
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, 'MEMBER', ?, ?, ?)
        """,
        assignmentId,
        incidentId.toString(),
        accountId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO police_phone (
          id, phone_code, display_name, account_id, status, registered,
          created_at, updated_at, version
        )
        VALUES (?::uuid, ?, 'Search path access phone', ?::uuid, 'ACTIVE', TRUE, ?, ?, 1)
        """,
        policePhoneId,
        "phone-" + suffix,
        accountId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, police_phone_id, status,
          started_by_account_id, started_at, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'ACTIVE', ?::uuid, ?, 1, ?, ?)
        """,
        dutyShiftId,
        opId.toString(),
        assignmentId,
        policePhoneId,
        accountId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
    jdbcTemplate.update(
        """
        INSERT INTO search_path (
          id, duty_shift_id, account_id, status, started_at, geometry,
          version, created_at, updated_at
        )
        VALUES (
          ?::uuid, ?::uuid, ?::uuid, 'RECORDING', ?,
          ST_GeomFromText('LINESTRING(126.91 35.16,126.92 35.17)', 4326),
          1, ?, ?
        )
        """,
        pathId,
        dutyShiftId,
        accountId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }
}
