package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.support.auth.WithMockAccount;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id VARCHAR(80) NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(80), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS missing_person (incident_id VARCHAR(36) PRIMARY KEY, display_name VARCHAR(120) NOT NULL, photo_object_key CLOB, appearance_text CLOB, last_seen_location_text VARCHAR(255), last_seen_at TIMESTAMP WITH TIME ZONE, imported_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS incident_assignment (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, account_id VARCHAR(80) NOT NULL, incident_role VARCHAR(32) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "DELETE FROM incident_assignment",
      "DELETE FROM missing_person",
      "DELETE FROM \"incident\"",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000003', 'mock-112-incident-closed', '종료된 배정 사건', 'CLOSED', '2026-04-27T09:00:00+09:00', '2026-04-28T12:00:00+09:00', 'acct-precinct-team', 7, '2026-04-27T09:00:00+09:00', '2026-04-28T12:00:00+09:00')",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000004', 'mock-112-incident-closed-stale-pii', '종료된 stale PII 사건', 'CLOSED', '2026-04-27T09:00:00+09:00', '2026-04-28T12:05:00+09:00', 'acct-precinct-team', 8, '2026-04-27T09:00:00+09:00', '2026-04-28T12:05:00+09:00')",
      "INSERT INTO missing_person (incident_id, display_name, photo_object_key, appearance_text, last_seen_location_text, last_seen_at, imported_at) VALUES ('10000000-0000-4000-8000-000000000004', '누출되면 안 되는 이름', 'mock-112/missing-person/stale', '누출되면 안 되는 인상착의', '누출되면 안 되는 위치', '2026-04-28T08:30:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 'acct-precinct-team', 'MEMBER', '2026-04-27T09:00:00+09:00', NULL, '2026-04-27T09:00:00+09:00', '2026-04-27T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000004', 'acct-precinct-team', 'MEMBER', '2026-04-27T09:00:00+09:00', NULL, '2026-04-27T09:00:00+09:00', '2026-04-27T09:00:00+09:00')"
    })
@DisplayName("L1-T05B GET /api/incidents terminal sanitized DTO 계약")
class IncidentTerminalReadDtoContractTest {

  private static final UUID CLOSED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000003");
  private static final UUID STALE_PII_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000004");
  private static final Set<String> TERMINAL_DETAIL_FIELDS =
      Set.of(
          "id",
          "incidentId",
          "status",
          "version",
          "closedAt",
          "terminalSnapshot",
          "writeDisabledReason");
  private static final Set<String> TERMINAL_SNAPSHOT_FIELDS =
      Set.of("id", "incidentId", "status", "version", "closedAt", "writeDisabledReason");
  private static final List<String> FORBIDDEN_PII_OR_INTERNAL_FIELDS =
      List.of(
          "missingPerson",
          "assignments",
          "displayName",
          "photoObjectKey",
          "appearanceText",
          "lastSeenLocationText",
          "lastSeenAt",
          "closedByAccountId",
          "confirmPersonalDataRemoval",
          "purgeRunId",
          "purgeDeadlineTs",
          "retentionDetail",
          "incidentDataPurge");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbc;

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.APP,
      accountId = "acct-precinct-team",
      policePhoneId = "dev-precinct-phone-01",
      roles = {Role.MEMBER})
  @DisplayName("종료 사건 상세는 terminalSnapshot만 반환하고 실종자 개인정보를 노출하지 않는다")
  void closedIncidentDetailReturnsSanitizedTerminalStateOnly() throws Exception {
    JsonNode body =
        readJson(
            mockMvc
                .perform(
                    get("/api/incidents/{incidentId}", CLOSED_INCIDENT_ID)
                        .header("Authorization", "Bearer app-terminal-detail")
                        .header("X-Client-Channel", "APP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(CLOSED_INCIDENT_ID.toString())))
                .andExpect(jsonPath("$.incidentId", is(CLOSED_INCIDENT_ID.toString())))
                .andExpect(jsonPath("$.status", is("CLOSED")))
                .andExpect(jsonPath("$.version", is(7)))
                .andExpect(jsonPath("$.closedAt", is("2026-04-28T03:00:00Z")))
                .andExpect(jsonPath("$.writeDisabledReason", is("incident_closed")))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    assertThat(fieldNames(body)).isEqualTo(TERMINAL_DETAIL_FIELDS);
    assertTerminalSnapshot(
        body.path("terminalSnapshot"), CLOSED_INCIDENT_ID, 7L, "2026-04-28T03:00:00Z");
    assertNoPiiOrInternalFields(body);
    assertThat(missingPersonCount(CLOSED_INCIDENT_ID)).isZero();
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "acct-precinct-team",
      roles = {Role.MEMBER})
  @DisplayName("stale missing_person row가 남아도 종료 사건 상세 DTO에는 PII가 섞이지 않는다")
  void closedIncidentDetailDoesNotLeakStaleMissingPersonRow() throws Exception {
    String rawBody =
        mockMvc
            .perform(
                get("/api/incidents/{incidentId}", STALE_PII_INCIDENT_ID)
                    .header("Authorization", "Bearer web-terminal-detail-stale-pii")
                    .header("X-Client-Channel", "WEB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(STALE_PII_INCIDENT_ID.toString())))
            .andExpect(jsonPath("$.status", is("CLOSED")))
            .andExpect(jsonPath("$.closedAt", is("2026-04-28T03:05:00Z")))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    JsonNode body = readJson(rawBody);
    assertThat(fieldNames(body)).isEqualTo(TERMINAL_DETAIL_FIELDS);
    assertNoPiiOrInternalFields(body);
    assertThat(rawBody)
        .doesNotContain(
            "누출되면 안 되는 이름",
            "mock-112/missing-person/stale",
            "누출되면 안 되는 인상착의",
            "누출되면 안 되는 위치");
  }

  private JsonNode readJson(String body) throws Exception {
    return objectMapper.readTree(body);
  }

  private void assertTerminalSnapshot(
      JsonNode snapshot, UUID incidentId, long version, String closedAt) {
    assertThat(fieldNames(snapshot)).isEqualTo(TERMINAL_SNAPSHOT_FIELDS);
    assertThat(snapshot.path("id").asText()).isEqualTo(incidentId.toString());
    assertThat(snapshot.path("incidentId").asText()).isEqualTo(incidentId.toString());
    assertThat(snapshot.path("status").asText()).isEqualTo("CLOSED");
    assertThat(snapshot.path("version").asLong()).isEqualTo(version);
    assertThat(snapshot.path("closedAt").asText()).isEqualTo(closedAt);
    assertThat(snapshot.path("writeDisabledReason").asText()).isEqualTo("incident_closed");
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new LinkedHashSet<>();
    Iterator<String> iterator = node.fieldNames();
    while (iterator.hasNext()) {
      names.add(iterator.next());
    }
    return names;
  }

  private static void assertNoPiiOrInternalFields(JsonNode body) {
    FORBIDDEN_PII_OR_INTERNAL_FIELDS.forEach(field -> assertThat(body.has(field)).isFalse());
    FORBIDDEN_PII_OR_INTERNAL_FIELDS.forEach(
        field -> assertThat(body.path("terminalSnapshot").has(field)).isFalse());
  }

  private int missingPersonCount(UUID incidentId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM missing_person WHERE incident_id = ?",
            Integer.class,
            incidentId.toString());
    return count == null ? 0 : count;
  }
}
