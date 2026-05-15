package com.surimap.harness.sc01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.ExternalIncident;
import com.surimap.external.ExternalIncidentAdapter;
import com.surimap.external.ExternalMissingPerson;
import com.surimap.external.ExternalSeedMarker;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.support.auth.WithMockAccount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** SC-01 사건 시작 흐름을 mock 112 입력부터 active read까지 실제 경계로 묶어 검증한다. */
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("L1-I01 SC-01 사건 시작 흐름 통합 검증")
@Tag("integration")
class Sc01IncidentStartIntegrationTest extends PostGisIntegrationTestSupport {

  private static final String SOURCE_INCIDENT_ID = "00000000-0000-0000-0000-000000000001";
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550001");
  private static final String COMMAND_ACCOUNT_CODE = "acct-precinct-cmd";
  private static final String CAR_ACCOUNT_CODE = "acct-precinct-car";
  private static final String TEAM_ACCOUNT_CODE = "acct-precinct-team";
  private static final String COMMAND_ACCOUNT_ID = "11111111-1111-1111-1111-111111110001";
  private static final String CAR_ACCOUNT_ID = "11111111-1111-1111-1111-111111110002";
  private static final String TEAM_ACCOUNT_ID = "11111111-1111-1111-1111-111111110003";

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private ExternalIncidentAdapter externalIncidentAdapter;

  @DynamicPropertySource
  static void useMainMigrationsWithAccountFixtures(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.flyway.locations", () -> "classpath:db/migration-test,classpath:db/migration");
  }

  @BeforeEach
  void cleanSc01Tables() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE marker_notification, photo, marker, event_dispatch_job, "
            + "operational_period, incident_assignment, missing_person, idempotency_record, "
            + "\"incident\" RESTART IDENTITY CASCADE");
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = COMMAND_ACCOUNT_CODE,
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("mock 112 import 후 OP1, 기준 마커, 이벤트 outbox, active read가 수렴한다")
  void mock112ImportCreatesOpenIncidentAndConvergesActiveRead() throws Exception {
    givenMock112Incident();

    mockMvc
        .perform(importRequest("idem-l1-i01-sc01"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("OPEN")))
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(
            jsonPath(
                "$.assignmentAccountIds",
                containsInAnyOrder(COMMAND_ACCOUNT_ID, CAR_ACCOUNT_ID, TEAM_ACCOUNT_ID)));

    assertThat(count("\"incident\"", "id = ? AND status = 'OPEN'", INCIDENT_ID)).isEqualTo(1);
    assertThat(count("missing_person", "incident_id = ?", INCIDENT_ID)).isEqualTo(1);
    assertThat(count("incident_assignment", "incident_id = ? AND revoked_at IS NULL", INCIDENT_ID))
        .isEqualTo(3);
    assertThat(
            count(
                "operational_period",
                "id = ? AND incident_id = ? AND sequence_number = 1 AND status = 'ACTIVE'",
                OP1_ID,
                INCIDENT_ID))
        .isEqualTo(1);
    assertThat(
            count(
                "marker",
                "id = ? AND incident_id = ? AND operational_period_id = ? "
                    + "AND marker_source = 'MOCK_SEED' AND status = 'ACTIVE'",
                MARKER_ID,
                INCIDENT_ID,
                OP1_ID))
        .isEqualTo(1);
    assertThat(eventTypesFor(INCIDENT_ID))
        .containsExactlyInAnyOrder("INCIDENT_CREATED", "OP_TRANSITIONED");

    mockMvc
        .perform(get("/api/incidents").header("X-Client-Channel", "WEB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.items[0].status", is("OPEN")))
        .andExpect(jsonPath("$.items[0].version", is(1)));

    mockMvc
        .perform(
            get("/api/incidents/{incidentId}", INCIDENT_ID)
                .header("X-Client-Channel", "WEB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("OPEN")))
        .andExpect(jsonPath("$.missingPerson.displayName", is("가상 실종자 001")))
        .andExpect(
            jsonPath(
                "$.assignments[*].accountId",
                containsInAnyOrder(COMMAND_ACCOUNT_ID, CAR_ACCOUNT_ID, TEAM_ACCOUNT_ID)));
  }

  private void givenMock112Incident() {
    when(externalIncidentAdapter.fetchIncident(SOURCE_INCIDENT_ID))
        .thenReturn(
            new ExternalIncident(
                SOURCE_INCIDENT_ID,
                "광주 무등산 탐방로 실종 신고",
                OffsetDateTime.parse("2026-04-28T00:00:00Z"),
                "READY",
                new ExternalMissingPerson(
                    "가상 실종자 001",
                    "mock-112/missing-person/mock-112-incident-001.jpg",
                    "검은색 상의, 회색 바지",
                    "무등산 서측 탐방로 입구",
                    OffsetDateTime.parse("2026-04-27T23:20:00Z")),
                List.of(
                    assignment("precinct-cmd", COMMAND_ACCOUNT_CODE, "FIELD_COMMANDER"),
                    assignment("precinct-car", CAR_ACCOUNT_CODE, "MEMBER"),
                    assignment("precinct-team", TEAM_ACCOUNT_CODE, "MEMBER")),
                List.of(
                    new ExternalSeedMarker("CLUE", "MOCK_SEED", "신고자 진술 위치", 126.9134, 35.1631))));
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

  private List<String> eventTypesFor(UUID incidentId) {
    return jdbcTemplate.queryForList(
        "SELECT event_type FROM event_dispatch_job WHERE incident_id = ? ORDER BY event_type",
        String.class,
        incidentId);
  }

  @TestConfiguration
  static class SearchAreaFixtureConfig {

    @Bean
    SearchAreaQuery searchAreaQuery() {
      return new SearchAreaQueryMock();
    }
  }
}
