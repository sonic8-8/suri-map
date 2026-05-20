package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/** Active 사건 목록·상세 DTO가 허용 필드만 노출하고 종료·파기 필드를 섞지 않는지 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id UUID NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(80), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS missing_person (incident_id VARCHAR(36) PRIMARY KEY, display_name VARCHAR(120) NOT NULL, photo_object_key CLOB, appearance_text CLOB, last_seen_location_text VARCHAR(255), last_seen_at TIMESTAMP WITH TIME ZONE, imported_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS incident_assignment (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, account_id VARCHAR(80) NOT NULL, incident_role VARCHAR(32) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS account (id VARCHAR(36) PRIMARY KEY, login_id VARCHAR(80) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(128) NOT NULL, account_type VARCHAR(32) NOT NULL, organization_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)",
      "MERGE INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) KEY(id) VALUES ('11111111-1111-1111-1111-111111110001', 'acct-precinct-cmd', '{noop}fixture', '종로 지구대 지휘', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE')",
      "MERGE INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) KEY(id) VALUES ('11111111-1111-1111-1111-111111110002', 'acct-precinct-car', '{noop}fixture', '종로 지구대 순찰차', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE')",
      "MERGE INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) KEY(id) VALUES ('11111111-1111-1111-1111-111111110003', 'acct-precinct-team', '{noop}fixture', '종로 지구대 팀', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE')",
      "MERGE INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) KEY(id) VALUES ('11111111-1111-1111-1111-111111110004', 'acct-cmd-alpha', '{noop}fixture', '실종팀 알파 지휘', 'COMMAND', 'MISSING_TEAM', 'ACTIVE')",
      "MERGE INTO account (id, login_id, password_hash, display_name, account_type, organization_type, status) KEY(id) VALUES ('11111111-1111-1111-1111-111111110005', 'acct-team-alpha', '{noop}fixture', '실종팀 알파 팀', 'TEAM', 'MISSING_TEAM', 'ACTIVE')",
      "DELETE FROM incident_assignment",
      "DELETE FROM missing_person",
      "DELETE FROM \"incident\"",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000001', '00000000-0000-0000-0000-000000000001', '광주 무등산 실종 신고', 'OPEN', '2026-04-28T09:00:00+09:00', NULL, NULL, 3, '2026-04-28T09:00:00+09:00', '2026-04-28T10:30:00+09:00')",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000002', '00000000-0000-0000-0000-000000000003', '미배정 OPEN 사건', 'OPEN', '2026-04-28T09:10:00+09:00', NULL, NULL, 1, '2026-04-28T09:10:00+09:00', '2026-04-28T09:10:00+09:00')",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000005', '00000000-0000-0000-0000-000000000004', '실종자 row 없는 OPEN 사건', 'OPEN', '2026-04-28T09:20:00+09:00', NULL, NULL, 2, '2026-04-28T09:20:00+09:00', '2026-04-28T09:20:00+09:00')",
      "INSERT INTO \"incident\" (id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id, version, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000006', '00000000-0000-0000-0000-000000000006', '실종팀 대원 배정 OPEN 사건', 'OPEN', '2026-04-28T09:30:00+09:00', NULL, NULL, 4, '2026-04-28T09:30:00+09:00', '2026-04-28T09:30:00+09:00')",
      "INSERT INTO missing_person (incident_id, display_name, photo_object_key, appearance_text, last_seen_location_text, last_seen_at, imported_at) VALUES ('10000000-0000-4000-8000-000000000001', '가상 실종자 001', 'mock-112/missing-person/001', '남색 점퍼, 회색 등산화', '무등산 서측 탐방로 입구', '2026-04-28T08:30:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO missing_person (incident_id, display_name, photo_object_key, appearance_text, last_seen_location_text, last_seen_at, imported_at) VALUES ('10000000-0000-4000-8000-000000000006', '가상 실종자 006', NULL, '검정 점퍼', '광주천 산책로', '2026-04-28T09:00:00+09:00', '2026-04-28T09:30:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111110001', 'FIELD_COMMANDER', '2026-04-28T09:00:00+09:00', NULL, '2026-04-28T09:00:00+09:00', '2026-04-28T09:00:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111110002', 'MEMBER', '2026-04-28T09:05:00+09:00', NULL, '2026-04-28T09:05:00+09:00', '2026-04-28T09:05:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111110003', 'MEMBER', '2026-04-28T09:10:00+09:00', NULL, '2026-04-28T09:10:00+09:00', '2026-04-28T09:10:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111110004', 'INCIDENT_COMMANDER', '2026-04-28T10:30:00+09:00', NULL, '2026-04-28T10:30:00+09:00', '2026-04-28T10:30:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111110005', 'MEMBER', '2026-04-28T10:35:00+09:00', NULL, '2026-04-28T10:35:00+09:00', '2026-04-28T10:35:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111119901', 'MEMBER', '2026-04-28T09:15:00+09:00', NULL, '2026-04-28T09:15:00+09:00', '2026-04-28T09:15:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111119902', 'MEMBER', '2026-04-28T09:20:00+09:00', NULL, '2026-04-28T09:20:00+09:00', '2026-04-28T09:20:00+09:00')",
      "INSERT INTO incident_assignment (id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000006', '11111111-1111-1111-1111-111111110005', 'MEMBER', '2026-04-28T09:30:00+09:00', NULL, '2026-04-28T09:30:00+09:00', '2026-04-28T09:30:00+09:00')"
    })
@DisplayName("L1-T05A GET /api/incidents active read DTO 계약")
class IncidentActiveReadDtoContractTest {

  private static final UUID OPEN_ASSIGNED_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final UUID OPEN_NO_MISSING_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000005");
  private static final UUID OPEN_MISSING_TEAM_MEMBER_INCIDENT_ID =
      UUID.fromString("10000000-0000-4000-8000-000000000006");
  private static final Set<String> ACTIVE_LIST_ITEM_FIELDS =
      Set.of("id", "incidentId", "title", "status", "version", "closedAt");
  private static final Set<String> ACTIVE_DETAIL_FIELDS =
      Set.of(
          "id",
          "incidentId",
          "title",
          "status",
          "openedAt",
          "version",
          "missingPerson",
          "assignments");
  private static final Set<String> DETAIL_MISSING_PERSON_FIELDS =
      Set.of(
          "incidentId",
          "displayName",
          "photoObjectKey",
          "photoUrl",
          "appearanceText",
          "lastSeenLocationText",
          "lastSeenAt");
  private static final Set<String> DETAIL_ASSIGNMENT_FIELDS =
      Set.of(
          "accountId",
          "accountDisplayName",
          "accountType",
          "organizationType",
          "incidentRole",
          "assignedAt");
  private static final List<String> TERMINAL_OR_PURGE_FIELDS =
      List.of(
          "terminalStatus",
          "terminalState",
          "incidentTerminal",
          "purgeStatus",
          "localPurgeState",
          "purgeRequestedAt",
          "purgedAt",
          "purgeJobId",
          "tombstone",
          "closedByAccountId",
          "confirmPersonalDataRemoval");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.APP,
      accountId = "acct-precinct-team",
      policePhoneId = "dev-precinct-phone-01",
      roles = {Role.MEMBER})
  @DisplayName("GET /api/incidents는 현재 계정에 배정된 OPEN 사건 목록과 list 허용 필드만 반환한다")
  void list_returns_only_open_assigned_incidents_with_active_list_fields() throws Exception {
    JsonNode body =
        readJson(
            mockMvc
                .perform(
                    get("/api/incidents")
                        .header("Authorization", "Bearer app-active-list")
                        .header("X-Client-Channel", "APP"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    JsonNode items = body.get("items");
    assertThat(items).isNotNull();
    assertThat(items.isArray()).isTrue();
    assertThat(items).hasSize(1);

    JsonNode item = items.get(0);
    assertThat(fieldNames(item)).isEqualTo(ACTIVE_LIST_ITEM_FIELDS);
    assertThat(item.path("id").asText()).isEqualTo(OPEN_ASSIGNED_INCIDENT_ID.toString());
    assertThat(item.path("incidentId").asText()).isEqualTo(OPEN_ASSIGNED_INCIDENT_ID.toString());
    assertThat(item.path("title").asText()).isEqualTo("광주 무등산 실종 신고");
    assertThat(item.path("status").asText()).isEqualTo("OPEN");
    assertThat(item.path("version").asLong()).isEqualTo(3L);
    assertThat(item.path("closedAt").isNull()).isTrue();
    assertNoTerminalOrPurgeFields(item);
    assertNoDetailOnlyFields(item);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "acct-cmd-alpha",
      roles = {Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER})
  @DisplayName("GET /api/incidents는 WEB 지휘 계정에 같은 조직 유형의 OPEN 사건 목록을 반환한다")
  void list_returns_organization_open_incidents_for_web_commander() throws Exception {
    JsonNode body =
        readJson(
            mockMvc
                .perform(
                    get("/api/incidents")
                        .header("Authorization", "Bearer web-active-list")
                        .header("X-Client-Channel", "WEB"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    JsonNode items = body.get("items");
    assertThat(items).isNotNull();
    assertThat(items.isArray()).isTrue();
    assertThat(items).hasSize(2);
    assertThat(items.findValues("incidentId").stream().map(JsonNode::asText).toList())
        .containsExactly(
            OPEN_MISSING_TEAM_MEMBER_INCIDENT_ID.toString(), OPEN_ASSIGNED_INCIDENT_ID.toString());
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = "acct-cmd-alpha",
      roles = {Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER})
  @DisplayName("GET /api/incidents/{incidentId}는 WEB 지휘 계정의 조직 범위 사건 상세를 반환한다")
  void detail_returns_organization_incident_for_web_commander() throws Exception {
    JsonNode body =
        readJson(
            mockMvc
                .perform(
                    get("/api/incidents/{incidentId}", OPEN_MISSING_TEAM_MEMBER_INCIDENT_ID)
                        .header("Authorization", "Bearer web-active-detail")
                        .header("X-Client-Channel", "WEB"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    assertThat(fieldNames(body)).isEqualTo(ACTIVE_DETAIL_FIELDS);
    assertThat(body.path("incidentId").asText())
        .isEqualTo(OPEN_MISSING_TEAM_MEMBER_INCIDENT_ID.toString());
    assertThat(body.path("title").asText()).isEqualTo("실종팀 대원 배정 OPEN 사건");
    assertThat(body.path("missingPerson").path("displayName").asText()).isEqualTo("가상 실종자 006");
    assertThat(body.path("assignments")).hasSize(1);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.APP,
      accountId = "acct-precinct-team",
      policePhoneId = "dev-precinct-phone-01",
      roles = {Role.MEMBER})
  @DisplayName("GET /api/incidents/{incidentId}는 OPEN 사건 상세와 detail 허용 필드만 반환한다")
  void detail_returns_open_incident_missing_person_and_assignments_without_terminal_fields()
      throws Exception {
    JsonNode body =
        readJson(
            mockMvc
                .perform(
                    get("/api/incidents/{incidentId}", OPEN_ASSIGNED_INCIDENT_ID)
                        .header("Authorization", "Bearer app-active-detail")
                        .header("X-Client-Channel", "APP"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    assertThat(fieldNames(body)).isEqualTo(ACTIVE_DETAIL_FIELDS);
    assertThat(body.path("id").asText()).isEqualTo(OPEN_ASSIGNED_INCIDENT_ID.toString());
    assertThat(body.path("incidentId").asText()).isEqualTo(OPEN_ASSIGNED_INCIDENT_ID.toString());
    assertThat(body.path("title").asText()).isEqualTo("광주 무등산 실종 신고");
    assertThat(body.path("status").asText()).isEqualTo("OPEN");
    assertThat(body.path("openedAt").asText()).isEqualTo("2026-04-28T00:00:00Z");
    assertThat(body.path("version").asLong()).isEqualTo(3L);

    JsonNode missingPerson = body.path("missingPerson");
    assertThat(fieldNames(missingPerson)).isEqualTo(DETAIL_MISSING_PERSON_FIELDS);
    assertThat(missingPerson.path("incidentId").asText())
        .isEqualTo(OPEN_ASSIGNED_INCIDENT_ID.toString());
    assertThat(missingPerson.path("displayName").asText()).isEqualTo("가상 실종자 001");
    assertThat(missingPerson.path("photoObjectKey").asText())
        .isEqualTo("mock-112/missing-person/001");
    assertThat(missingPerson.path("photoUrl").asText())
        .isEqualTo("/suri-map-photo/mock-112/missing-person/001");
    assertThat(missingPerson.path("appearanceText").asText()).isEqualTo("남색 점퍼, 회색 등산화");
    assertThat(missingPerson.path("lastSeenLocationText").asText()).isEqualTo("무등산 서측 탐방로 입구");
    assertThat(missingPerson.path("lastSeenAt").asText()).isEqualTo("2026-04-27T23:30:00Z");

    JsonNode assignments = body.path("assignments");
    assertThat(assignments.isArray()).isTrue();
    assertThat(assignments).hasSize(5);
    assignments.forEach(
        assignment -> assertThat(fieldNames(assignment)).isEqualTo(DETAIL_ASSIGNMENT_FIELDS));
    assertThat(accountIds(assignments))
        .containsExactlyInAnyOrder(
            "11111111-1111-1111-1111-111111110001",
            "11111111-1111-1111-1111-111111110002",
            "11111111-1111-1111-1111-111111110003",
            "11111111-1111-1111-1111-111111110004",
            "11111111-1111-1111-1111-111111110005");
    JsonNode commander = assignmentByAccountId(assignments, "11111111-1111-1111-1111-111111110001");
    assertThat(commander.path("accountDisplayName").asText()).isEqualTo("종로 지구대 지휘");
    assertThat(commander.path("accountType").asText()).isEqualTo("COMMAND");
    assertThat(commander.path("organizationType").asText()).isEqualTo("POLICE_SUBSTATION");
    assertThat(commander.path("incidentRole").asText()).isEqualTo("FIELD_COMMANDER");
    assertThat(commander.path("assignedAt").asText()).isEqualTo("2026-04-28T00:00:00Z");
    assertNoTerminalOrPurgeFields(body);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111119901",
      policePhoneId = "dev-other-phone-01",
      roles = {Role.MEMBER})
  @DisplayName("GET /api/incidents/{incidentId}는 다른 사건 배정 계정을 incident_access_denied로 거부한다")
  void detail_rejects_account_assigned_to_other_incident() throws Exception {
    mockMvc
        .perform(
            get("/api/incidents/{incidentId}", OPEN_ASSIGNED_INCIDENT_ID)
                .header("Authorization", "Bearer app-active-detail-denied")
                .header("X-Client-Channel", "APP"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("incident_access_denied"));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111119902",
      policePhoneId = "dev-no-missing-phone-01",
      roles = {Role.MEMBER})
  @DisplayName(
      "GET /api/incidents/{incidentId}는 OPEN missing_person row가 없어도 active detail 키를 유지한다")
  void detail_keeps_active_missing_person_key_when_row_is_absent() throws Exception {
    JsonNode body =
        readJson(
            mockMvc
                .perform(
                    get("/api/incidents/{incidentId}", OPEN_NO_MISSING_INCIDENT_ID)
                        .header("Authorization", "Bearer app-active-detail-no-missing")
                        .header("X-Client-Channel", "APP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OPEN_NO_MISSING_INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.incidentId").value(OPEN_NO_MISSING_INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.title").value("실종자 row 없는 OPEN 사건"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openedAt").value("2026-04-28T00:20:00Z"))
                .andExpect(jsonPath("$.version").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

    assertThat(fieldNames(body)).isEqualTo(ACTIVE_DETAIL_FIELDS);
    assertThat(body.has("missingPerson")).isTrue();
    assertThat(body.path("missingPerson").isNull()).isTrue();
    assertThat(body.path("assignments").isArray()).isTrue();
    assertThat(body.path("assignments")).hasSize(1);
    assertNoTerminalOrPurgeFields(body);
  }

  private JsonNode readJson(String body) throws Exception {
    return objectMapper.readTree(body);
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new LinkedHashSet<>();
    Iterator<String> iterator = node.fieldNames();
    while (iterator.hasNext()) {
      names.add(iterator.next());
    }
    return names;
  }

  private static List<String> accountIds(JsonNode assignments) {
    return assignments.findValues("accountId").stream().map(JsonNode::asText).toList();
  }

  private static JsonNode assignmentByAccountId(JsonNode assignments, String accountId) {
    for (JsonNode assignment : assignments) {
      if (assignment.path("accountId").asText().equals(accountId)) {
        return assignment;
      }
    }
    throw new AssertionError("assignment not found: " + accountId);
  }

  private static void assertNoTerminalOrPurgeFields(JsonNode node) {
    TERMINAL_OR_PURGE_FIELDS.forEach(field -> assertThat(node.has(field)).isFalse());
  }

  private static void assertNoDetailOnlyFields(JsonNode node) {
    assertThat(node.has("missingPerson")).isFalse();
    assertThat(node.has("assignments")).isFalse();
  }
}
