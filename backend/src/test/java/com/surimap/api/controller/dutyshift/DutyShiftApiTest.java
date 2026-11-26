package com.surimap.api.controller.dutyshift;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.support.auth.WithMockAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@DisplayName("근무 API의 계정 기준 기록")
class DutyShiftApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000005801");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000005801");
  private static final String FIELD_ACCOUNT_ID = "62000000-0000-0000-0000-000000005801";
  private static final String REVOKED_ACCOUNT_ID = "62000000-0000-0000-0000-000000005802";
  private static final String COMMANDER_ID = "63000000-0000-0000-0000-000000005801";
  private static final UUID FIELD_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-000000005801");
  private static final UUID REVOKED_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-000000005802");
  private static final UUID COMMANDER_ASSIGNMENT_ID =
      UUID.fromString("61000000-0000-0000-0000-000000005803");
  private static final UUID REVOKED_DUTY_SHIFT_ID =
      UUID.fromString("77000000-0000-0000-0000-000000005802");
  // 이 업무폰 ID는 어떤 근무와도 연결되지 않는다.
  private static final String POLICE_PHONE_ID = "50000000-0000-0000-0000-000000005801";
  private static final Instant BASE_TS = Instant.parse("2026-11-25T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void seedIncidentWithRevokedAssignment() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          event_dispatch_job, idempotency_record, duty_shift, operational_period,
          incident_assignment, account, incident
        CASCADE
        """);
    Timestamp base = Timestamp.from(BASE_TS);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, gen_random_uuid(), 'Duty shift account incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        base,
        base,
        base);
    insertAccount(FIELD_ACCOUNT_ID, "acct-duty-field", "광주경찰청 실종팀 현장1팀", "TEAM", "MISSING_TEAM");
    insertAccount(
        REVOKED_ACCOUNT_ID, "acct-duty-revoked", "광주경찰청 실종팀 현장2팀", "TEAM", "MISSING_TEAM");
    insertAccount(COMMANDER_ID, "acct-duty-command", "동부경찰서 상황실", "COMMAND", "POLICE_SUBSTATION");
    insertAssignment(FIELD_ASSIGNMENT_ID, FIELD_ACCOUNT_ID, "MEMBER", null);
    // 현장2팀은 근무를 마친 뒤 사건 배정이 해제됐다.
    insertAssignment(REVOKED_ASSIGNMENT_ID, REVOKED_ACCOUNT_ID, "MEMBER", base);
    insertAssignment(COMMANDER_ASSIGNMENT_ID, COMMANDER_ID, "INCIDENT_COMMANDER", null);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_by_account_id,
          started_at, version, created_at, updated_at
        )
        VALUES (?, ?, 1, 'ACTIVE', 'INITIAL_IMPORT', ?::uuid, ?, 1, ?, ?)
        """,
        OP_ID,
        INCIDENT_ID,
        COMMANDER_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, status, started_by_account_id,
          ended_by_account_id, started_at, ended_at, version, created_at, updated_at
        )
        VALUES (?, ?, ?, 'ENDED', ?::uuid, ?::uuid, ?, ?, 2, ?, ?)
        """,
        REVOKED_DUTY_SHIFT_ID,
        OP_ID,
        REVOKED_ASSIGNMENT_ID,
        REVOKED_ACCOUNT_ID,
        REVOKED_ACCOUNT_ID,
        base,
        base,
        base,
        base);
  }

  @Test
  @WithMockAccount(
      accountId = FIELD_ACCOUNT_ID,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      policePhoneId = POLICE_PHONE_ID)
  @DisplayName("본문에 업무폰 ID가 없어도 근무가 시작되고 응답에는 업무폰 대신 근무한 계정 이름이 담긴다")
  void start_duty_shift_without_police_phone_id_in_body_returns_account_name() throws Exception {
    // when & then: 업무폰 ID 없이 근무 시작 요청을 보낸다.
    mockMvc
        .perform(
            post("/api/duty-shifts")
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", POLICE_PHONE_ID)
                .header("Idempotency-Key", "idem-duty-start-5801")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "%s",
                      "opId": "%s",
                      "clientTs": "2026-11-25T09:00:00+09:00"
                    }
                    """
                        .formatted(INCIDENT_ID, OP_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.accountId", is(FIELD_ACCOUNT_ID)))
        .andExpect(jsonPath("$.accountDisplayName", is("광주경찰청 실종팀 현장1팀")))
        .andExpect(jsonPath("$.policePhoneId").doesNotExist())
        .andExpect(jsonPath("$.policePhoneLabel").doesNotExist());
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER_ID,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB)
  @DisplayName("근무 조회에는 업무폰 ID 필터가 없어 업무폰 ID를 보내도 사건의 근무가 모두 나온다")
  void list_duty_shifts_ignores_police_phone_id_filter() throws Exception {
    // given: 현장1팀이 근무를 시작한다.
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, status, started_by_account_id,
          started_at, version, created_at, updated_at
        )
        VALUES (gen_random_uuid(), ?, ?, 'ACTIVE', ?::uuid, now(), 1, now(), now())
        """,
        OP_ID,
        FIELD_ASSIGNMENT_ID,
        FIELD_ACCOUNT_ID);

    // when & then: 근무와 연결되지 않은 업무폰 ID를 함께 보낸다.
    mockMvc
        .perform(
            get("/api/duty-shifts")
                .header("X-Client-Channel", "WEB")
                .param("incidentId", INCIDENT_ID.toString())
                .param("policePhoneId", POLICE_PHONE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(
            jsonPath(
                "$.items[*].accountDisplayName",
                containsInAnyOrder("광주경찰청 실종팀 현장1팀", "광주경찰청 실종팀 현장2팀")))
        .andExpect(jsonPath("$.items[*].policePhoneId").isEmpty())
        .andExpect(jsonPath("$.items[*].policePhoneLabel").isEmpty());
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER_ID,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB)
  @DisplayName("사건 배정이 해제된 계정의 근무도 그 계정 이름으로 조회된다")
  void list_duty_shifts_of_revoked_assignment_returns_account_name() throws Exception {
    // when & then: 배정이 해제된 현장2팀 계정으로 거른다.
    mockMvc
        .perform(
            get("/api/duty-shifts")
                .header("X-Client-Channel", "WEB")
                .param("incidentId", INCIDENT_ID.toString())
                .param("accountId", REVOKED_ACCOUNT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id", is(REVOKED_DUTY_SHIFT_ID.toString())))
        .andExpect(jsonPath("$.items[0].accountId", is(REVOKED_ACCOUNT_ID)))
        .andExpect(jsonPath("$.items[0].accountDisplayName", is("광주경찰청 실종팀 현장2팀")));
  }

  private void insertAccount(
      String accountId,
      String loginId,
      String displayName,
      String accountType,
      String organizationType) {
    Timestamp base = Timestamp.from(BASE_TS);
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?, '{noop}fixture', ?, ?, ?, 'ACTIVE', ?, ?)
        """,
        accountId,
        loginId,
        displayName,
        accountType,
        organizationType,
        base,
        base);
  }

  private void insertAssignment(
      UUID assignmentId, String accountId, String incidentRole, Timestamp revokedAt) {
    Timestamp base = Timestamp.from(BASE_TS);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at,
          updated_at
        )
        VALUES (?, ?, ?::uuid, ?, ?, ?, ?, ?)
        """,
        assignmentId,
        INCIDENT_ID,
        accountId,
        incidentRole,
        base,
        revokedAt,
        base,
        base);
  }
}
