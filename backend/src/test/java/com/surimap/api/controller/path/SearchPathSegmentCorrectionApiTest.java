package com.surimap.api.controller.path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.support.auth.WithMockAccount;
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
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc(addFilters = false)
class SearchPathSegmentCorrectionApiTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_A = UUID.fromString("10000000-0000-0000-0000-000000004601");
  private static final UUID INCIDENT_B = UUID.fromString("10000000-0000-0000-0000-000000004602");
  private static final UUID OP_A = UUID.fromString("70000000-0000-0000-0000-000000004601");
  private static final UUID OP_B = UUID.fromString("70000000-0000-0000-0000-000000004602");
  private static final String FIELD_A = "62000000-0000-0000-0000-000000004601";
  private static final String FIELD_B = "62000000-0000-0000-0000-000000004602";
  private static final String COMMANDER = "63000000-0000-0000-0000-000000004601";
  private static final String OTHER_ACCOUNT = "63000000-0000-0000-0000-000000004602";
  private static final String SEGMENT_A = "71000000-0000-0000-0000-000000004601";
  private static final String SEGMENT_B = "71000000-0000-0000-0000-000000004602";
  private static final String ASSIGNMENT_A = "61000000-0000-0000-0000-000000004601";
  private static final String CORRECTION_BODY =
      "{\"movementType\":\"FOOT\",\"reason\":\"manual correction\"}";
  private static final Instant BASE_TS = Instant.parse("2026-11-16T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void seedIncidents() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          idempotency_record,
          event_dispatch_job,
          search_path_segment,
          search_path,
          duty_shift,
          operational_period,
          incident_assignment,
          account,
          incident
        CASCADE
        """);
    insertAccount(FIELD_A, "TEAM", "POLICE_SUBSTATION");
    insertAccount(FIELD_B, "TEAM", "MISSING_TEAM");
    insertAccount(COMMANDER, "COMMAND", "POLICE_SUBSTATION");
    insertAccount(OTHER_ACCOUNT, "COMMAND", "POLICE_SUBSTATION");
    // 사건 A에는 지휘 계정과 같은 조직 유형의 계정이 배정돼 있다. 사건 B에는 없다.
    insertIncidentWithOp(INCIDENT_A, OP_A);
    insertIncidentWithOp(INCIDENT_B, OP_B);
    insertAssignedSegment(INCIDENT_A, OP_A, FIELD_A, "4601");
    insertAssignedSegment(INCIDENT_B, OP_B, FIELD_B, "4602");
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.APP,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("경로 구간 보정을 APP 채널로 요청하면 channel_not_allowed로 거절되고 경로 구간이 바뀌지 않는다")
  void app_channel_correction_is_denied() throws Exception {
    // when & then: 지휘 계정이 APP 채널로 조직 범위 안의 경로 구간을 보정한다.
    correct(SEGMENT_A, "idem-46-app")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    assertSegmentUnchanged(SEGMENT_A);
  }

  @Test
  @WithMockAccount(accountId = FIELD_A, channel = Channel.WEB, roles = Role.MEMBER)
  @DisplayName("웹 MEMBER 계정이 경로 구간을 보정하면 role_denied로 거절되고 경로 구간이 바뀌지 않는다")
  void web_member_correction_is_denied() throws Exception {
    // when & then: 사건 A에 배정된 MEMBER 계정이 웹으로 자기 사건의 경로 구간을 보정한다.
    correct(SEGMENT_A, "idem-46-member")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("role_denied")));

    assertSegmentUnchanged(SEGMENT_A);
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("지휘 계정이 조직 범위 밖의 사건의 경로 구간을 웹으로 보정하면 incident_access_denied로 거절된다")
  void command_account_outside_organization_scope_is_denied() throws Exception {
    // when & then: 지휘 계정이 같은 조직 유형의 배정이 없는 사건 B의 경로 구간을 보정한다.
    correct(SEGMENT_B, "idem-46-outside")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));

    assertSegmentUnchanged(SEGMENT_B);
    assertThat(rowCount("event_dispatch_job")).isZero();
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("종료된 사건의 경로 구간을 보정하면 incident_closed로 거절되고 경로 구간이 바뀌지 않는다")
  void closed_incident_correction_is_denied() throws Exception {
    // given: 조직 범위 안의 사건 A가 종료됐다.
    closeIncident(INCIDENT_A);

    // when & then: 지휘 계정이 종료된 사건의 경로 구간을 보정한다.
    correct(SEGMENT_A, "idem-46-closed")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("incident_closed")));

    assertSegmentUnchanged(SEGMENT_A);
    assertThat(rowCount("event_dispatch_job")).isZero();
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("사건 권한이 없는 지휘 계정이 종료된 사건의 경로 구간을 보정하면 incident_closed보다 incident_access_denied로 먼저 거절된다")
  void closed_incident_outside_organization_scope_is_denied_as_access_denied() throws Exception {
    // given: 조직 범위 밖의 사건 B가 종료됐다.
    closeIncident(INCIDENT_B);

    // when & then: 사건 권한이 없는 지휘 계정이 종료된 사건 B의 경로 구간을 보정한다. 이 계정은 사건의 종료 여부를 알 수 없어야 한다.
    correct(SEGMENT_B, "idem-46-outside-closed")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")));

    assertSegmentUnchanged(SEGMENT_B);
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("조직 범위 안의 웹 지휘 계정이 경로 구간을 보정하면 지금과 같은 응답을 받고 보정 내용이 저장된다")
  void command_account_in_organization_scope_corrects_segment() throws Exception {
    // when & then: 지휘 계정이 개인 배정 없이 조직 범위 안의 사건 A의 경로 구간을 보정한다.
    correct(SEGMENT_A, "idem-46-in-scope")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(SEGMENT_A)))
        .andExpect(jsonPath("$.movementType", is("FOOT")))
        .andExpect(jsonPath("$.movementTypeSource", is("MANUAL")))
        .andExpect(jsonPath("$.opId", is(OP_A.toString())))
        .andExpect(jsonPath("$.correctedByAccountId", is(COMMANDER)))
        .andExpect(jsonPath("$.correctedAt").exists())
        .andExpect(jsonPath("$.version", is(2)));

    // then: 경로 구간에 보정 내용과 보정한 계정이 남는다. 경로 구간 변경 이벤트는 한 건 저장된다.
    assertThat(segmentRow(SEGMENT_A))
        .containsEntry("movement_type", "FOOT")
        .containsEntry("movement_type_source", "MANUAL")
        .containsEntry("corrected_by_account_id", COMMANDER)
        .containsEntry("version", 2L);
    assertThat(rowCount("event_dispatch_job")).isEqualTo(1);
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("X-Account-Id에 다른 계정을 보내도 보정한 계정은 토큰의 계정으로 저장된다")
  void corrected_by_account_comes_from_token_not_header() throws Exception {
    // when & then: 지휘 계정이 X-Account-Id 헤더에 다른 계정을 적어 보정한다.
    mockMvc
        .perform(
            patch("/api/search-path-segments/{id}", SEGMENT_A)
                .header("X-Account-Id", OTHER_ACCOUNT)
                .header("Idempotency-Key", "idem-46-header")
                .contentType("application/json")
                .content(CORRECTION_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correctedByAccountId", is(COMMANDER)));

    assertThat(segmentRow(SEGMENT_A)).containsEntry("corrected_by_account_id", COMMANDER);
  }

  @Test
  @WithMockAccount(
      accountId = COMMANDER,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      roles = Role.FIELD_COMMANDER)
  @DisplayName("같은 Idempotency-Key의 저장된 응답이 있어도 조직 범위 밖이 된 웹 지휘 계정의 보정은 incident_access_denied로 거절된다")
  void stored_response_is_not_replayed_to_command_account_outside_organization_scope()
      throws Exception {
    // given: 지휘 계정이 조직 범위 안에서 보정해 응답이 저장됐다.
    correct(SEGMENT_A, "idem-46-replay").andExpect(status().isOk());
    // given: 사건 A의 같은 조직 유형 배정이 회수돼 지휘 계정이 조직 범위 밖이 됐다.
    jdbcTemplate.update(
        "UPDATE incident_assignment SET revoked_at = ? WHERE id = ?::uuid",
        Timestamp.from(BASE_TS.plusSeconds(60)),
        ASSIGNMENT_A);

    // when & then: 지휘 계정이 같은 Idempotency-Key와 본문으로 다시 보정한다.
    correct(SEGMENT_A, "idem-46-replay")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("incident_access_denied")))
        .andExpect(jsonPath("$.id").doesNotExist());

    // then: 처음 보정한 결과만 남는다.
    assertThat(segmentRow(SEGMENT_A)).containsEntry("version", 2L);
    assertThat(rowCount("idempotency_record")).isEqualTo(1);
    assertThat(rowCount("event_dispatch_job")).isEqualTo(1);
  }

  // 헤더가 없어서 거절되는 경우와 구분하려고, 예전 요청처럼 X-Account-Id에 지휘 계정을 함께 넣었다.
  private ResultActions correct(String segmentId, String idempotencyKey) throws Exception {
    return mockMvc.perform(
        patch("/api/search-path-segments/{id}", segmentId)
            .header("X-Account-Id", COMMANDER)
            .header("Idempotency-Key", idempotencyKey)
            .contentType("application/json")
            .content(CORRECTION_BODY));
  }

  private void assertSegmentUnchanged(String segmentId) {
    assertThat(segmentRow(segmentId))
        .containsEntry("movement_type", "VEHICLE")
        .containsEntry("movement_type_source", "AUTO")
        .containsEntry("corrected_by_account_id", null)
        .containsEntry("version", 1L);
    assertThat(rowCount("idempotency_record")).isZero();
  }

  private Map<String, Object> segmentRow(String segmentId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT movement_type, movement_type_source,
               corrected_by_account_id::text AS corrected_by_account_id, version
        FROM search_path_segment
        WHERE id = ?::uuid
        """,
        segmentId);
  }

  private Integer rowCount(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
  }

  private void closeIncident(UUID incidentId) {
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = ? WHERE id = ?::uuid",
        Timestamp.from(BASE_TS.plusSeconds(3600)),
        incidentId.toString());
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
        VALUES (?::uuid, gen_random_uuid(), 'Segment correction incident', 'OPEN', ?, 1, ?, ?)
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

  // 테스트 준비에서 이 도우미로 사건 배정, 근무, 수색 경로, 자동 분류된 경로 구간을 계정마다 한 개씩 넣는다.
  private void insertAssignedSegment(UUID incidentId, UUID opId, String accountId, String suffix) {
    String assignmentId = "61000000-0000-0000-0000-00000000" + suffix;
    String dutyShiftId = "60000000-0000-0000-0000-00000000" + suffix;
    String pathId = "81000000-0000-0000-0000-00000000" + suffix;
    String segmentId = "71000000-0000-0000-0000-00000000" + suffix;
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
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, status,
          started_by_account_id, started_at, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, 'ACTIVE', ?::uuid, ?, 1, ?, ?)
        """,
        dutyShiftId,
        opId.toString(),
        assignmentId,
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
    jdbcTemplate.update(
        """
        INSERT INTO search_path_segment (
          id, search_path_id, movement_type, movement_type_source, geometry,
          started_at, ended_at, start_point_order, end_point_order, last_changed_path_version,
          version, created_at, updated_at
        )
        VALUES (
          ?::uuid, ?::uuid, 'VEHICLE', 'AUTO',
          ST_GeomFromText('LINESTRING(126.91 35.16,126.92 35.17)', 4326),
          ?, ?, 0, 1, 1, 1, ?, ?
        )
        """,
        segmentId,
        pathId,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS.plusSeconds(10)),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }
}
