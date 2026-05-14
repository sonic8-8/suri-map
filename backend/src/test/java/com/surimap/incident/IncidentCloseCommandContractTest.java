package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.mock112.AssignmentPollingHandler;
import com.surimap.incident.event.IncidentClosedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import com.surimap.incident.lifecycle.IncidentLifecycleGuardException;
import com.surimap.support.auth.WithMockAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(IncidentCloseCommandContractTest.FixedClockConfig.class)
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id UUID NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(36), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS missing_person (incident_id VARCHAR(36) PRIMARY KEY, display_name VARCHAR(120) NOT NULL, photo_object_key CLOB, appearance_text CLOB, last_seen_location_text VARCHAR(255), last_seen_at TIMESTAMP WITH TIME ZONE, imported_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS incident_assignment (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, account_id VARCHAR(80) NOT NULL, incident_role VARCHAR(32) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS idempotency_record (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36), idempotency_key VARCHAR(160) NOT NULL, request_body_hash VARCHAR(64) NOT NULL, request_path VARCHAR(200) NOT NULL, request_method VARCHAR(16) NOT NULL, idempotency_status VARCHAR(32) NOT NULL, response_status_code INTEGER, response_body_json CLOB, response_body_format_version INTEGER, result_entity_type VARCHAR(80), result_entity_id VARCHAR(36), result_entity_status VARCHAR(32), result_entity_version BIGINT, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "DELETE FROM idempotency_record",
      "DELETE FROM incident_assignment",
      "DELETE FROM missing_person",
      "DELETE FROM \"incident\"",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '00000000-0000-0000-0000-000000000001', '종로구 인왕산 실종 신고', 'OPEN', '2026-04-28T09:00:00+09:00', NULL, NULL, 1, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO missing_person (incident_id, display_name, photo_object_key, appearance_text, last_seen_location_text, last_seen_at, imported_at) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '가상 실종자 001', 'mock-112/missing-person/mock-112-incident-001.jpg', '남색 점퍼, 회색 등산화', '인왕산 북측 산책로 입구', '2026-04-28T08:30:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000010', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', '11111111-1111-1111-1111-111111110010', 'INCIDENT_COMMANDER', '2026-04-28T09:00:00+09:00', NULL, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')"
    })
@DisplayName("L1-T06 SC-12 사건 종료 command와 purge handoff 계약")
class IncidentCloseCommandContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final String SOURCE_INCIDENT_ID = "00000000-0000-0000-0000-000000000001";
  private static final String COMMANDER_ACCOUNT_ID = "11111111-1111-1111-1111-111111110010";
  private static final Instant CLOSED_AT = Instant.parse("2026-04-28T01:45:00Z");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private AssignmentPollingHandler assignmentPollingHandler;

  @MockitoBean private IncidentEventPublisher incidentEventPublisher;

  @BeforeEach
  void resetState() {
    reset(incidentEventPublisher);
    assignmentPollingHandler.reset();
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = COMMANDER_ACCOUNT_ID,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("WEB 지휘관 close는 CLOSED 전이, missing_person hard delete, INCIDENT_CLOSED 발행을 보장한다")
  void commanderClosesIncidentAndPublishesSanitizedTerminalState() throws Exception {
    mockMvc
        .perform(
            post("/api/incidents/{incidentId}/close", INCIDENT_ID)
                .header("Authorization", "Bearer close-command")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-l1-t06-close-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "closeReason": "SC12_COMPLETE",
                      "confirmPersonalDataRemoval": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("CLOSED")))
        .andExpect(jsonPath("$.version", is(2)))
        .andExpect(jsonPath("$.closedAt", is("2026-04-28T01:45:00Z")))
        .andExpect(jsonPath("$.writeDisabledReason", is("incident_closed")))
        .andExpect(jsonPath("$.terminalSnapshot.id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.terminalSnapshot.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.terminalSnapshot.status", is("CLOSED")))
        .andExpect(jsonPath("$.terminalSnapshot.version", is(2)))
        .andExpect(jsonPath("$.terminalSnapshot.closedAt", is("2026-04-28T01:45:00Z")))
        .andExpect(jsonPath("$.terminalSnapshot.writeDisabledReason", is("incident_closed")))
        // terminalSnapshot은 S3-2가 소비할 sanitized state라서 실종자 PII를 노출하지 않는다.
        .andExpect(jsonPath("$.terminalSnapshot.displayName").doesNotExist())
        .andExpect(jsonPath("$.terminalSnapshot.photoObjectKey").doesNotExist())
        .andExpect(jsonPath("$.terminalSnapshot.photoUrl").doesNotExist())
        .andExpect(jsonPath("$.terminalSnapshot.appearanceText").doesNotExist())
        .andExpect(jsonPath("$.terminalSnapshot.lastSeenLocationText").doesNotExist());

    assertThat(singleString("SELECT status FROM \"incident\" WHERE id = ?")).isEqualTo("CLOSED");
    assertThat(singleLong("SELECT version FROM \"incident\" WHERE id = ?")).isEqualTo(2L);
    assertThat(singleString("SELECT closed_by_account_id FROM \"incident\" WHERE id = ?"))
        .isEqualTo(COMMANDER_ACCOUNT_ID);
    assertThat(missingPersonCount()).isZero();

    verify(incidentEventPublisher)
        .publishIncidentClosed(argThat(IncidentCloseCommandContractTest::closedEventMatches));
    // close handoff는 INCIDENT_CLOSED 하나만 발행하고, 배정 변경 이벤트를 섞지 않는다.
    verify(incidentEventPublisher, never()).publishIncidentAssignmentChanged(any());

    // SC-12의 "close 이후 112/mock 배정 갱신 차단"은 S1-1 lifecycle guard로 관찰한다.
    assertThatThrownBy(
            () ->
                assignmentPollingHandler.handleAssignmentChanges(
                    SOURCE_INCIDENT_ID, List.of(supportAssignment())))
        .isInstanceOf(IncidentLifecycleGuardException.class)
        .hasMessage("incident_closed");
    assertThat(activeAssignmentAccountIds()).doesNotContain("acct-support-cmd");
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = COMMANDER_ACCOUNT_ID,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("개인정보 삭제 확인이 false면 close를 실행하지 않는다")
  void closeRequiresPersonalDataRemovalConfirmation() throws Exception {
    mockMvc
        .perform(
            post("/api/incidents/{incidentId}/close", INCIDENT_ID)
                .header("Authorization", "Bearer close-command")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-l1-t06-close-confirm-false")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "closeReason": "SC12_COMPLETE",
                      "confirmPersonalDataRemoval": false
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));

    assertThat(singleString("SELECT status FROM \"incident\" WHERE id = ?")).isEqualTo("OPEN");
    assertThat(missingPersonCount()).isEqualTo(1);
    verify(incidentEventPublisher, never()).publishIncidentClosed(any());
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = COMMANDER_ACCOUNT_ID,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("APP 채널은 사건 종료 command를 호출할 수 없다")
  void appChannelCannotCloseIncident() throws Exception {
    mockMvc
        .perform(
            post("/api/incidents/{incidentId}/close", INCIDENT_ID)
                .header("Authorization", "Bearer close-command")
                .header("X-Client-Channel", "APP")
                .header("Idempotency-Key", "idem-l1-t06-close-app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "closeReason": "SC12_COMPLETE",
                      "confirmPersonalDataRemoval": true
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    assertThat(singleString("SELECT status FROM \"incident\" WHERE id = ?")).isEqualTo("OPEN");
    assertThat(missingPersonCount()).isEqualTo(1);
    verify(incidentEventPublisher, never()).publishIncidentClosed(any());
  }

  private static boolean closedEventMatches(IncidentClosedEvent event) {
    return event.id().equals(INCIDENT_ID)
        && event.status().equals("CLOSED")
        && event.version() == 2L
        && event.closedAt().equals(CLOSED_AT)
        && event.writeDisabledReason().equals("incident_closed");
  }

  private String singleString(String sql) {
    return jdbc.queryForObject(sql, String.class, INCIDENT_ID.toString());
  }

  private long singleLong(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class, INCIDENT_ID.toString());
    return value == null ? 0L : value;
  }

  private int missingPersonCount() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM missing_person WHERE incident_id = ?",
            Integer.class,
            INCIDENT_ID.toString());
    return count == null ? 0 : count;
  }

  private List<String> activeAssignmentAccountIds() {
    return jdbc.queryForList(
        """
        SELECT account_id
        FROM incident_assignment
        WHERE incident_id = ?
          AND revoked_at IS NULL
        ORDER BY account_id ASC
        """,
        String.class,
        INCIDENT_ID.toString());
  }

  private static ExternalAssignment supportAssignment() {
    return new ExternalAssignment(
        SOURCE_INCIDENT_ID + ":support-cmd",
        "acct-support-cmd",
        "FIELD_COMMANDER",
        OffsetDateTime.parse("2026-04-28T11:00:00+09:00"));
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(CLOSED_AT, ZoneOffset.UTC);
    }
  }
}
