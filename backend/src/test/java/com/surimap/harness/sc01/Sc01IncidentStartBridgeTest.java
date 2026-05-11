package com.surimap.harness.sc01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.ExternalIncident;
import com.surimap.external.ExternalIncidentAdapter;
import com.surimap.external.ExternalMissingPerson;
import com.surimap.external.ExternalSeedMarker;
import com.surimap.marker.domain.port.ReferenceMarkerSeed;
import com.surimap.marker.domain.port.ReferenceMarkerSeed.SeedMarker;
import com.surimap.support.auth.WithMockAccount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/** Docker 없이 SC-01 import가 실제 OP1 생성과 EventHub publish port까지 연결됐는지 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id VARCHAR(80) NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(36), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS missing_person (incident_id VARCHAR(36) PRIMARY KEY, display_name VARCHAR(120) NOT NULL, photo_object_key CLOB, appearance_text CLOB, last_seen_location_text VARCHAR(255), last_seen_at TIMESTAMP WITH TIME ZONE, imported_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS incident_assignment (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, account_id VARCHAR(80) NOT NULL, incident_role VARCHAR(32) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS operational_period (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, sequence_number INTEGER NOT NULL, status VARCHAR(32) NOT NULL, reason VARCHAR(32) NOT NULL, reason_memo CLOB, started_by_account_id VARCHAR(36), ended_by_account_id VARCHAR(36), started_at TIMESTAMP WITH TIME ZONE NOT NULL, ended_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS idempotency_record (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36), idempotency_key VARCHAR(160) NOT NULL, request_body_hash VARCHAR(64) NOT NULL, request_path VARCHAR(200) NOT NULL, request_method VARCHAR(16) NOT NULL, idempotency_status VARCHAR(32) NOT NULL, response_status_code INTEGER, response_body_json CLOB, response_body_format_version INTEGER, result_entity_type VARCHAR(80), result_entity_id VARCHAR(36), result_entity_status VARCHAR(32), result_entity_version BIGINT, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "DELETE FROM idempotency_record",
      "DELETE FROM operational_period",
      "DELETE FROM incident_assignment",
      "DELETE FROM missing_person",
      "DELETE FROM \"incident\""
    })
@DisplayName("L1-I01 SC-01 사건 시작 bridge 검증")
class Sc01IncidentStartBridgeTest {

  private static final String SOURCE_INCIDENT_ID = "mock-112-incident-001";
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final String COMMAND_ACCOUNT_ID = "acct-precinct-cmd";
  private static final String CAR_ACCOUNT_ID = "acct-precinct-car";
  private static final String TEAM_ACCOUNT_ID = "acct-precinct-team";

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private ExternalIncidentAdapter externalIncidentAdapter;

  @MockitoBean private ReferenceMarkerSeed referenceMarkerSeed;

  @MockitoBean private EventHub eventHub;

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = COMMAND_ACCOUNT_ID,
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("import는 실제 OP1 생성 후 INCIDENT_CREATED와 OP_TRANSITIONED publish를 요청한다")
  void importCreatesOp1AndPublishesIncidentStartEvents() throws Exception {
    givenMock112Incident();

    mockMvc
        .perform(importRequest("idem-l1-i01-bridge"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("OPEN")))
        .andExpect(
            jsonPath(
                "$.assignmentAccountIds",
                containsInAnyOrder(COMMAND_ACCOUNT_ID, CAR_ACCOUNT_ID, TEAM_ACCOUNT_ID)));

    assertThat(count("operational_period", "incident_id = ? AND sequence_number = 1", INCIDENT_ID))
        .isEqualTo(1);
    verify(referenceMarkerSeed)
        .createForIncident(
            eq(INCIDENT_ID),
            eq(List.of(new SeedMarker("CLUE", "MOCK_SEED", "신고자 진술 위치", 126.9565, 37.5712))));

    ArgumentCaptor<PublishRequest> publishCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(eventHub, times(2)).publish(publishCaptor.capture());
    assertThat(publishCaptor.getAllValues().stream().map(PublishRequest::type).toList())
        .containsExactlyInAnyOrder("INCIDENT_CREATED", "OP_TRANSITIONED");
    assertThat(
            publishCaptor.getAllValues().stream()
                .filter(request -> "INCIDENT_CREATED".equals(request.type()))
                .findFirst()
                .orElseThrow()
                .payload())
        .containsEntry("id", INCIDENT_ID.toString())
        .containsEntry("status", "OPEN")
        .containsEntry("version", 1L);
  }

  private void givenMock112Incident() {
    when(externalIncidentAdapter.fetchIncident(SOURCE_INCIDENT_ID))
        .thenReturn(
            new ExternalIncident(
                SOURCE_INCIDENT_ID,
                "인왕산 북측 산책로 실종 신고",
                OffsetDateTime.parse("2026-04-28T00:00:00Z"),
                "READY",
                new ExternalMissingPerson(
                    "가상 실종자 001",
                    "mock-112/missing-person/mock-112-incident-001.jpg",
                    "검은색 상의, 회색 바지",
                    "인왕산 북측 산책로 입구",
                    OffsetDateTime.parse("2026-04-27T23:20:00Z")),
                List.of(
                    assignment("precinct-cmd", COMMAND_ACCOUNT_ID, "FIELD_COMMANDER"),
                    assignment("precinct-car", CAR_ACCOUNT_ID, "MEMBER"),
                    assignment("precinct-team", TEAM_ACCOUNT_ID, "MEMBER")),
                List.of(
                    new ExternalSeedMarker("CLUE", "MOCK_SEED", "신고자 진술 위치", 126.9565, 37.5712))));
  }

  private ExternalAssignment assignment(String key, String accountId, String role) {
    return new ExternalAssignment(
        SOURCE_INCIDENT_ID + ":" + key,
        accountId,
        role,
        OffsetDateTime.parse("2026-04-28T00:00:00Z"));
  }

  private org.springframework.test.web.servlet.RequestBuilder importRequest(String idempotencyKey) {
    return post("/api/incidents/import")
        .header("Authorization", "Bearer test-web")
        .header("X-Client-Channel", "WEB")
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"sourceIncidentId\":\"" + SOURCE_INCIDENT_ID + "\"}");
  }

  private int count(String table, String whereClause, Object... args) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause, Integer.class, args);
    return count == null ? 0 : count;
  }
}
