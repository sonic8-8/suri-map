package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.mock112.AssignmentPollingHandler;
import com.surimap.incident.event.IncidentAssignmentChangedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import com.surimap.support.auth.WithMockAccount;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id UUID NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(80), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS missing_person (incident_id VARCHAR(36) PRIMARY KEY, display_name VARCHAR(120) NOT NULL, photo_object_key CLOB, appearance_text CLOB, last_seen_location_text VARCHAR(255), last_seen_at TIMESTAMP WITH TIME ZONE, imported_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS incident_assignment (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, account_id VARCHAR(80) NOT NULL, incident_role VARCHAR(32) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS account (id VARCHAR(36) PRIMARY KEY, login_id VARCHAR(80) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(128) NOT NULL, account_type VARCHAR(32) NOT NULL, organization_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)",
      "CREATE TABLE IF NOT EXISTS police_phone (id VARCHAR(36) PRIMARY KEY, phone_code VARCHAR(80) NOT NULL UNIQUE, display_name VARCHAR(128) NOT NULL, account_id VARCHAR(36) NOT NULL, status VARCHAR(32) NOT NULL, last_heartbeat_at TIMESTAMP WITH TIME ZONE, last_sync_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)",
      "CREATE TABLE IF NOT EXISTS operational_period (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, sequence_number INTEGER NOT NULL, status VARCHAR(32) NOT NULL, reason VARCHAR(32) NOT NULL, reason_memo CLOB, started_by_account_id VARCHAR(80), ended_by_account_id VARCHAR(80), started_at TIMESTAMP WITH TIME ZONE NOT NULL, ended_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS search_path_seed_probe (id VARCHAR(80) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, op_id VARCHAR(36) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS marker_seed_probe (id VARCHAR(80) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, op_id VARCHAR(36) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS handover_memo_seed_probe (id VARCHAR(80) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, op_id VARCHAR(36) NOT NULL)",
      "DELETE FROM police_phone",
      "DELETE FROM account",
      "DELETE FROM handover_memo_seed_probe",
      "DELETE FROM marker_seed_probe",
      "DELETE FROM search_path_seed_probe",
      "DELETE FROM operational_period",
      "DELETE FROM incident_assignment",
      "DELETE FROM missing_person",
      "DELETE FROM \"incident\"",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110001', 'acct-precinct-cmd', '{noop}fixture', '지구대 지휘관', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE')",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110002', 'acct-precinct-car', '{noop}fixture', '지구대 순찰차', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE')",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110003', 'acct-precinct-team', '{noop}fixture', '지구대 현장팀', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE')",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110004', 'acct-cmd-alpha', '{noop}fixture', '실종팀 지휘관', 'COMMAND', 'MISSING_TEAM', 'ACTIVE')",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110005', 'acct-team-alpha', '{noop}fixture', '실종팀 현장팀', 'TEAM', 'MISSING_TEAM', 'ACTIVE')",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110006', 'acct-support-cmd', '{noop}fixture', '지원부대 지휘관', 'COMMAND', 'SUPPORT_UNIT', 'ACTIVE')",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110007', 'acct-support-car', '{noop}fixture', '지원부대 순찰차', 'PATROL_CAR', 'SUPPORT_UNIT', 'ACTIVE')",
      "INSERT INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) VALUES ('11111111-1111-1111-1111-111111110008', 'acct-support-team', '{noop}fixture', '지원부대 현장팀', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('00000000-0000-0000-0000-000000000201', 'dev-precinct-cmd-phone-01', '지구대 지휘관 단말', '11111111-1111-1111-1111-111111110001', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('50000000-0000-0000-0000-000000000001', 'dev-precinct-car-01', '지구대 순찰차 단말', '11111111-1111-1111-1111-111111110002', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('00000000-0000-0000-0000-000000000101', 'dev-precinct-phone-01', '지구대 현장팀 단말', '11111111-1111-1111-1111-111111110003', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('00000000-0000-0000-0000-000000000204', 'dev-alpha-cmd-phone-01', '실종팀 지휘관 단말', '11111111-1111-1111-1111-111111110004', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('00000000-0000-0000-0000-000000000205', 'dev-alpha-phone-01', '실종팀 현장팀 단말', '11111111-1111-1111-1111-111111110005', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('00000000-0000-0000-0000-000000000206', 'dev-support-cmd-phone-01', '지원부대 지휘관 단말', '11111111-1111-1111-1111-111111110006', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('00000000-0000-0000-0000-000000000207', 'dev-support-car-01', '지원부대 순찰차 단말', '11111111-1111-1111-1111-111111110007', 'ACTIVE')",
      "INSERT INTO police_phone (id, phone_code, display_name, account_id, status) VALUES ('00000000-0000-0000-0000-000000000208', 'dev-support-phone-01', '지원부대 현장팀 단말', '11111111-1111-1111-1111-111111110008', 'ACTIVE')",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '00000000-0000-0000-0000-000000000001', '광주 무등산 실종 신고', 'OPEN', '2026-04-28T09:00:00+09:00', NULL, NULL, 1, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO missing_person (incident_id, display_name, photo_object_key, appearance_text, last_seen_location_text, last_seen_at, imported_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '가상 실종자 001', 'mock-112/missing-person/mock-112-incident-001.jpg', '남색 점퍼, 회색 등산화', '무등산 서측 탐방로 입구', '2026-04-28T08:30:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO operational_period (id, incident_id, sequence_number, status, reason, reason_memo, started_by_account_id, ended_by_account_id, started_at, ended_at, version, created_at, updated_at) VALUES ('88888888-8888-8888-8888-888888880001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 1, 'ACTIVE', 'INITIAL', NULL, '11111111-1111-1111-1111-111111110001', NULL, '2026-04-28T09:00:00+09:00', NULL, 1, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO search_path_seed_probe (id, incident_id, op_id) VALUES ('path-precinct-car-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '88888888-8888-8888-8888-888888880001')",
      "INSERT INTO search_path_seed_probe (id, incident_id, op_id) VALUES ('path-precinct-foot-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '88888888-8888-8888-8888-888888880001')",
      "INSERT INTO marker_seed_probe (id, incident_id, op_id) VALUES ('mk-precinct-clue-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '88888888-8888-8888-8888-888888880001')",
      "INSERT INTO handover_memo_seed_probe (id, incident_id, op_id) VALUES ('memo-precinct-handover-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '88888888-8888-8888-8888-888888880001')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-04-28T09:00:00+09:00', NULL, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '11111111-1111-1111-1111-111111110002', 'MEMBER', '2026-04-28T09:00:00+09:00', NULL, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '11111111-1111-1111-1111-111111110003', 'MEMBER', '2026-04-28T09:00:00+09:00', NULL, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')"
    })
@DisplayName("L1-T04 SC-02 실종팀 인계와 112/mock 지원 배정 계약")
class IncidentHandoverSupportAssignmentContractTest {

  private static final String SOURCE_INCIDENT_ID = "00000000-0000-0000-0000-000000000001";
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final String OP1_ID = "88888888-8888-8888-8888-888888880001";

  @Autowired private AssignmentPollingHandler assignmentPollingHandler;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private MockMvc mockMvc;

  @Autowired private ApplicationContext applicationContext;

  @MockitoBean private IncidentEventPublisher incidentEventPublisher;

  @BeforeEach
  void resetPollingState() {
    assignmentPollingHandler.reset();
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.SUPPORT_UNIT,
      channel = Channel.APP,
      accountId = "acct-support-team",
      policePhoneId = "00000000-0000-0000-0000-000000000208",
      roles = {Role.MEMBER})
  @DisplayName("인계·지원 배정 후 지원 팀은 같은 사건을 조회하고 OP1 seed 기록은 유지된다")
  void handoverAndSupportAssignmentsAppendRowsAndAllowSupportTeamRead() throws Exception {
    assignmentPollingHandler.registerInitialAssignments(initialAssignments());

    assignmentPollingHandler.handleAssignmentChanges(
        SOURCE_INCIDENT_ID,
        concat(initialAssignments(), handoverAssignments(), supportAssignments()));

    assertThat(activeAssignmentAccountIds())
        .containsExactlyInAnyOrder(
            "11111111-1111-1111-1111-111111110001",
            "11111111-1111-1111-1111-111111110002",
            "11111111-1111-1111-1111-111111110003",
            "11111111-1111-1111-1111-111111110004",
            "11111111-1111-1111-1111-111111110005",
            "11111111-1111-1111-1111-111111110006",
            "11111111-1111-1111-1111-111111110007",
            "11111111-1111-1111-1111-111111110008");
    assertThat(revokedAssignmentCount()).isZero();
    assertOp1SeedEvidenceStillBelongsToSameIncidentAndOp();

    mockMvc
        .perform(
            get("/api/incidents/{incidentId}", INCIDENT_ID)
                .header("Authorization", "Bearer support-assignment-read")
                .header("X-Client-Channel", "APP"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("OPEN")))
        .andExpect(jsonPath("$.assignments", hasSize(8)))
        .andExpect(
            jsonPath(
                "$.assignments[*].accountId",
                containsInAnyOrder(
                    "11111111-1111-1111-1111-111111110001",
                    "11111111-1111-1111-1111-111111110002",
                    "11111111-1111-1111-1111-111111110003",
                    "11111111-1111-1111-1111-111111110004",
                    "11111111-1111-1111-1111-111111110005",
                    "11111111-1111-1111-1111-111111110006",
                    "11111111-1111-1111-1111-111111110007",
                    "11111111-1111-1111-1111-111111110008")));

    verify(incidentEventPublisher)
        .publishIncidentAssignmentChanged(
            argThat(
                event ->
                    assignmentChangedEventMatches(
                        event,
                        List.of(
                            "11111111-1111-1111-1111-111111110004",
                            "11111111-1111-1111-1111-111111110005",
                            "11111111-1111-1111-1111-111111110006",
                            "11111111-1111-1111-1111-111111110007",
                            "11111111-1111-1111-1111-111111110008"))));
  }

  @Test
  @DisplayName("IncidentAssignmentView.notificationTargets는 지원 배정 대상 단말만 반환한다")
  void notificationTargetsForSupportAssignmentReturnOnlyNewSupportTeamAndPatrolPhones()
      throws Exception {
    assignmentPollingHandler.registerInitialAssignments(initialAssignments());
    assignmentPollingHandler.handleAssignmentChanges(
        SOURCE_INCIDENT_ID,
        concat(initialAssignments(), handoverAssignments(), supportAssignments()));

    Object targets =
        incidentAssignmentViewType()
            .getMethod("notificationTargets", UUID.class, String.class)
            .invoke(incidentAssignmentView(), INCIDENT_ID, "SUPPORT_ASSIGNMENT");

    assertThat(stringList(targets, "accountIds"))
        .containsExactlyInAnyOrder(
            "11111111-1111-1111-1111-111111110007",
            "11111111-1111-1111-1111-111111110008");
    assertThat(stringList(targets, "policePhoneIds"))
        .containsExactlyInAnyOrder(
            "00000000-0000-0000-0000-000000000207",
            "00000000-0000-0000-0000-000000000208");
  }

  private static boolean assignmentChangedEventMatches(
      IncidentAssignmentChangedEvent event, List<String> changedAccountIds) {
    return event.id().equals(INCIDENT_ID)
        && event.status().equals("ACTIVE")
        && event.version() > 1L
        && event.changedAccountIds().equals(changedAccountIds);
  }

  private Object incidentAssignmentView() {
    return applicationContext.getBean(incidentAssignmentViewType());
  }

  private static Class<?> incidentAssignmentViewType() {
    try {
      return Class.forName("com.surimap.incident.service.IncidentAssignmentView");
    } catch (ClassNotFoundException exception) {
      throw new AssertionError(
          "S1-1 must provide IncidentAssignmentView.notificationTargets(incidentId, targetPolicy)",
          exception);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringList(Object target, String accessorName) {
    try {
      Method method = target.getClass().getMethod(accessorName);
      Object value = method.invoke(target);
      assertThat(value).isInstanceOf(List.class);
      return (List<String>) value;
    } catch (NoSuchMethodException exception) {
      throw new AssertionError("notification target result must expose " + accessorName, exception);
    } catch (IllegalAccessException exception) {
      throw new AssertionError(exception);
    } catch (InvocationTargetException exception) {
      throw new AssertionError(exception.getCause());
    }
  }

  private List<String> activeAssignmentAccountIds() {
    return jdbc.queryForList(
        """
        SELECT account_id
        FROM incident_assignment
        WHERE incident_id = ?
          AND revoked_at IS NULL
        ORDER BY assigned_at ASC, account_id ASC
        """,
        String.class,
        INCIDENT_ID.toString());
  }

  private int revokedAssignmentCount() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM incident_assignment WHERE incident_id = ? AND revoked_at IS NOT NULL",
            Integer.class,
            INCIDENT_ID.toString());
    return count == null ? 0 : count;
  }

  private void assertOp1SeedEvidenceStillBelongsToSameIncidentAndOp() {
    assertThat(evidenceCount("search_path_seed_probe", "path-precinct-car-001")).isEqualTo(1);
    assertThat(evidenceCount("search_path_seed_probe", "path-precinct-foot-001")).isEqualTo(1);
    assertThat(evidenceCount("marker_seed_probe", "mk-precinct-clue-001")).isEqualTo(1);
    assertThat(evidenceCount("handover_memo_seed_probe", "memo-precinct-handover-001"))
        .isEqualTo(1);
  }

  private int evidenceCount(String tableName, String id) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE id = ? AND incident_id = ? AND op_id = ?",
            Integer.class,
            id,
            INCIDENT_ID.toString(),
            OP1_ID);
    return count == null ? 0 : count;
  }

  private static List<ExternalAssignment> initialAssignments() {
    return List.of(
        assignment(
            SOURCE_INCIDENT_ID + ":precinct-cmd",
            "acct-precinct-cmd",
            "FIELD_COMMANDER",
            "2026-04-28T09:00:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":precinct-car",
            "acct-precinct-car",
            "MEMBER",
            "2026-04-28T09:00:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":precinct-team",
            "acct-precinct-team",
            "MEMBER",
            "2026-04-28T09:00:00+09:00"));
  }

  private static List<ExternalAssignment> handoverAssignments() {
    return List.of(
        assignment(
            SOURCE_INCIDENT_ID + ":cmd-alpha",
            "acct-cmd-alpha",
            "INCIDENT_COMMANDER",
            "2026-04-28T10:30:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":team-alpha",
            "acct-team-alpha",
            "MEMBER",
            "2026-04-28T10:30:00+09:00"));
  }

  private static List<ExternalAssignment> supportAssignments() {
    return List.of(
        assignment(
            SOURCE_INCIDENT_ID + ":support-cmd",
            "acct-support-cmd",
            "FIELD_COMMANDER",
            "2026-04-28T11:00:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":support-car",
            "acct-support-car",
            "MEMBER",
            "2026-04-28T11:00:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":support-team",
            "acct-support-team",
            "MEMBER",
            "2026-04-28T11:00:00+09:00"));
  }

  private static ExternalAssignment assignment(
      String externalAssignmentKey, String accountId, String incidentRole, String assignedAt) {
    return new ExternalAssignment(
        externalAssignmentKey, accountId, incidentRole, OffsetDateTime.parse(assignedAt));
  }

  @SafeVarargs
  private static <T> List<T> concat(List<T>... values) {
    return java.util.stream.Stream.of(values).flatMap(List::stream).toList();
  }
}
