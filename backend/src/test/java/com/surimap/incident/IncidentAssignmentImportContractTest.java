package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.surimap.external.ExternalAssignment;
import com.surimap.external.mock112.AssignmentPollingHandler;
import com.surimap.incident.event.IncidentAssignmentChangedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id UUID NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(80), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS incident_assignment (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, account_id VARCHAR(80) NOT NULL, incident_role VARCHAR(32) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS operational_period (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, sequence_number INTEGER NOT NULL, status VARCHAR(32) NOT NULL, reason VARCHAR(32) NOT NULL, reason_memo CLOB, started_by_account_id VARCHAR(36), ended_by_account_id VARCHAR(36), started_at TIMESTAMP WITH TIME ZONE NOT NULL, ended_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS search_path_seed_probe (id VARCHAR(80) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, op_id VARCHAR(80) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS marker_seed_probe (id VARCHAR(80) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, op_id VARCHAR(80) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS handover_memo_seed_probe (id VARCHAR(80) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, op_id VARCHAR(80) NOT NULL)",
      "DELETE FROM handover_memo_seed_probe",
      "DELETE FROM marker_seed_probe",
      "DELETE FROM search_path_seed_probe",
      "DELETE FROM operational_period",
      "DELETE FROM incident_assignment",
      "DELETE FROM \"incident\"",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '00000000-0000-0000-0000-000000000001', '종로구 인왕산 실종 신고', 'OPEN', '2026-04-28T09:00:00+09:00', NULL, NULL, 1, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-04-28T09:00:00+09:00', NULL, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '11111111-1111-1111-1111-111111110002', 'MEMBER', '2026-04-28T09:05:00+09:00', NULL, '2026-04-28T09:05:00+09:00', '2026-04-28T09:05:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '11111111-1111-1111-1111-111111110003', 'MEMBER', '2026-04-28T09:10:00+09:00', NULL, '2026-04-28T09:10:00+09:00', '2026-04-28T09:10:00+09:00')",
      "INSERT INTO operational_period (id, incident_id, sequence_number, status, reason, reason_memo, started_by_account_id, ended_by_account_id, started_at, ended_at, version, created_at, updated_at) VALUES ('op-precinct-001-op1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 1, 'ACTIVE', 'INITIAL', NULL, NULL, NULL, '2026-04-28T09:00:00+09:00', NULL, 1, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO search_path_seed_probe (id, incident_id, op_id) VALUES ('path-precinct-car-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 'op-precinct-001-op1')",
      "INSERT INTO search_path_seed_probe (id, incident_id, op_id) VALUES ('path-precinct-foot-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 'op-precinct-001-op1')",
      "INSERT INTO marker_seed_probe (id, incident_id, op_id) VALUES ('mk-precinct-clue-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 'op-precinct-001-op1')",
      "INSERT INTO handover_memo_seed_probe (id, incident_id, op_id) VALUES ('memo-precinct-handover-001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', 'op-precinct-001-op1')"
    })
@DisplayName("L1-T04 SC-02 112/mock incident_assignment import")
class IncidentAssignmentImportContractTest {

  private static final String SOURCE_INCIDENT_ID = "00000000-0000-0000-0000-000000000001";
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");

  @Autowired private AssignmentPollingHandler assignmentPollingHandler;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private IncidentEventPublisher incidentEventPublisher;

  @BeforeEach
  void resetPollingState() {
    assignmentPollingHandler.reset();
  }

  @Test
  @DisplayName("인계·지원 배정은 기존 지구대 OP1 기록과 배정을 유지하고 신규 배정만 추가한다")
  void handoverAndSupportAssignmentsPreserveOp1EvidenceAndAppendAssignments() {
    assignmentPollingHandler.handleAssignmentChanges(SOURCE_INCIDENT_ID, handoverAndSupport());

    assertThat(activeAssignmentAccountIds())
        .containsExactly(
            "11111111-1111-1111-1111-111111110001",
            "11111111-1111-1111-1111-111111110002",
            "11111111-1111-1111-1111-111111110003",
            "11111111-1111-1111-1111-111111110004",
            "11111111-1111-1111-1111-111111110005",
            "11111111-1111-1111-1111-111111110006",
            "11111111-1111-1111-1111-111111110007",
            "11111111-1111-1111-1111-111111110008");
    assertThat(count("incident_assignment", "revoked_at IS NOT NULL")).isZero();
    assertThat(count("operational_period", "id = 'op-precinct-001-op1'")).isEqualTo(1);
    assertThat(count("search_path_seed_probe", "op_id = 'op-precinct-001-op1'")).isEqualTo(2);
    assertThat(count("marker_seed_probe", "id = 'mk-precinct-clue-001'")).isEqualTo(1);
    assertThat(count("handover_memo_seed_probe", "id = 'memo-precinct-handover-001'")).isEqualTo(1);
    assertThat(incidentVersion()).isEqualTo(2L);

    verify(incidentEventPublisher)
        .publishIncidentAssignmentChanged(
            argThat(
                event ->
                    eventMatches(
                        event,
                        List.of(
                            "11111111-1111-1111-1111-111111110004",
                            "11111111-1111-1111-1111-111111110005",
                            "11111111-1111-1111-1111-111111110006",
                            "11111111-1111-1111-1111-111111110007",
                            "11111111-1111-1111-1111-111111110008"))));
  }

  @Test
  @DisplayName("같은 112 assignment key polling 재실행은 row와 이벤트를 중복 생성하지 않는다")
  void duplicatePollingDoesNotDuplicateAssignmentRowsOrEvent() {
    assignmentPollingHandler.handleAssignmentChanges(SOURCE_INCIDENT_ID, handoverAndSupport());
    assignmentPollingHandler.handleAssignmentChanges(SOURCE_INCIDENT_ID, handoverAndSupport());

    assertThat(count("incident_assignment", "1 = 1")).isEqualTo(8);
    assertThat(incidentVersion()).isEqualTo(2L);
    verify(incidentEventPublisher)
        .publishIncidentAssignmentChanged(argThat(event -> event.version() == 2L));
  }

  @Test
  @DisplayName("이미 active row가 있는 계정만 들어온 polling은 변경 이벤트를 만들지 않는다")
  void existingActiveAssignmentsDoNotPublishAssignmentChanged() {
    assignmentPollingHandler.handleAssignmentChanges(
        SOURCE_INCIDENT_ID,
        List.of(
            assignment(
                SOURCE_INCIDENT_ID + ":precinct-cmd",
                "acct-precinct-cmd",
                "FIELD_COMMANDER",
                "2026-04-28T09:00:00+09:00"),
            assignment(
                SOURCE_INCIDENT_ID + ":precinct-car",
                "acct-precinct-car",
                "MEMBER",
                "2026-04-28T09:05:00+09:00")));

    assertThat(count("incident_assignment", "1 = 1")).isEqualTo(3);
    assertThat(incidentVersion()).isEqualTo(1L);
    verify(incidentEventPublisher, never())
        .publishIncidentAssignmentChanged(org.mockito.ArgumentMatchers.any());
  }

  private static boolean eventMatches(
      IncidentAssignmentChangedEvent event, List<String> changedAccountIds) {
    return event.id().equals(INCIDENT_ID)
        && event.status().equals("ACTIVE")
        && event.version() == 2L
        && event.changedAccountIds().equals(changedAccountIds);
  }

  private List<String> activeAssignmentAccountIds() {
    return jdbc.queryForList(
        "SELECT account_id FROM incident_assignment WHERE incident_id = ? AND revoked_at IS NULL ORDER BY assigned_at ASC, account_id ASC",
        String.class,
        INCIDENT_ID.toString());
  }

  private long incidentVersion() {
    return jdbc.queryForObject(
        "SELECT version FROM \"incident\" WHERE id = ?", Long.class, INCIDENT_ID.toString());
  }

  private long count(String table, String where) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
  }

  private static List<ExternalAssignment> handoverAndSupport() {
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
            "2026-04-28T10:35:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":support-cmd",
            "acct-support-cmd",
            "FIELD_COMMANDER",
            "2026-04-28T10:40:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":support-car",
            "acct-support-car",
            "MEMBER",
            "2026-04-28T10:45:00+09:00"),
        assignment(
            SOURCE_INCIDENT_ID + ":support-team",
            "acct-support-team",
            "MEMBER",
            "2026-04-28T10:50:00+09:00"));
  }

  private static ExternalAssignment assignment(
      String key, String accountId, String role, String assignedAt) {
    return new ExternalAssignment(key, accountId, role, OffsetDateTime.parse(assignedAt));
  }
}
