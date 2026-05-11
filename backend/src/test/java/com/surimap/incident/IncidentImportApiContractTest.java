package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import com.surimap.incident.event.IncidentCreatedEvent;
import com.surimap.incident.event.IncidentEventPublisher;
import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.marker.domain.port.ReferenceMarkerSeed;
import com.surimap.marker.domain.port.ReferenceMarkerSeed.SeedMarker;
import com.surimap.operationalperiod.command.InitialOperationalPeriodCreator;
import com.surimap.operationalperiod.command.InitialOperationalPeriodResult;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import com.surimap.support.auth.WithMockAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
      "CREATE TABLE IF NOT EXISTS \"incident\" (id VARCHAR(36) PRIMARY KEY, source_incident_id VARCHAR(80) NOT NULL UNIQUE, title VARCHAR(200) NOT NULL, status VARCHAR(32) NOT NULL, opened_at TIMESTAMP WITH TIME ZONE, closed_at TIMESTAMP WITH TIME ZONE, closed_by_account_id VARCHAR(36), version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS missing_person (incident_id VARCHAR(36) PRIMARY KEY, display_name VARCHAR(120) NOT NULL, photo_object_key CLOB, appearance_text CLOB, last_seen_location_text VARCHAR(255), last_seen_at TIMESTAMP WITH TIME ZONE, imported_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS incident_assignment (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, account_id VARCHAR(80) NOT NULL, incident_role VARCHAR(32) NOT NULL, assigned_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS operational_period (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL, sequence_number INTEGER NOT NULL, status VARCHAR(32) NOT NULL, reason VARCHAR(32) NOT NULL, reason_memo CLOB, started_by_account_id VARCHAR(36), ended_by_account_id VARCHAR(36), started_at TIMESTAMP WITH TIME ZONE NOT NULL, ended_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS event_dispatch_job (id VARCHAR(36) PRIMARY KEY, event_id VARCHAR(120) NOT NULL, incident_id VARCHAR(36) NOT NULL, event_type VARCHAR(80) NOT NULL, payload_format_version INTEGER NOT NULL, payload CLOB NOT NULL, source_entity_type VARCHAR(80) NOT NULL, source_entity_id VARCHAR(36) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, dispatch_status VARCHAR(32) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "CREATE TABLE IF NOT EXISTS idempotency_record (id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36), idempotency_key VARCHAR(160) NOT NULL, request_body_hash VARCHAR(64) NOT NULL, request_path VARCHAR(200) NOT NULL, request_method VARCHAR(16) NOT NULL, idempotency_status VARCHAR(32) NOT NULL, response_status_code INTEGER, response_body_json CLOB, response_body_format_version INTEGER, result_entity_type VARCHAR(80), result_entity_id VARCHAR(36), result_entity_status VARCHAR(32), result_entity_version BIGINT, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)",
      "DELETE FROM idempotency_record",
      "DELETE FROM event_dispatch_job",
      "DELETE FROM operational_period",
      "DELETE FROM incident_assignment",
      "DELETE FROM missing_person",
      "DELETE FROM \"incident\""
    })
@DisplayName("L1-T01 POST /api/incidents/import 계약")
class IncidentImportApiContractTest {

  private static final String SOURCE_INCIDENT_ID = "mock-112-incident-001";
  private static final UUID INCIDENT_ID = BoundaryAreaFixtures.INCIDENT_ID;
  private static final UUID OP1_ID = BoundaryAreaFixtures.OP1_ID;
  private static final UUID ACCOUNT_PRECINCT_COMMANDER =
      UUID.fromString("11111111-1111-1111-1111-111111110001");
  private static final UUID ACCOUNT_PRECINCT_CAR =
      UUID.fromString("11111111-1111-1111-1111-111111110002");
  private static final UUID ACCOUNT_PRECINCT_TEAM =
      UUID.fromString("11111111-1111-1111-1111-111111110003");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private ExternalIncidentAdapter externalIncidentAdapter;

  @MockitoBean private InitialOperationalPeriodCreator initialOperationalPeriodCreator;

  @MockitoBean private ReferenceMarkerSeed referenceMarkerSeed;

  @MockitoBean private IncidentEventPublisher incidentEventPublisher;

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName(
      "SC-01 import가 incident/missing_person/incident_assignment/OP1/초기 마커/INCIDENT_CREATED를 만든다")
  void importCreatesIncidentMissingPersonAssignmentOp1AndIncidentCreatedEvent() throws Exception {
    givenMock112Incident(SOURCE_INCIDENT_ID);
    givenOp1CreatorWritesOp1();
    givenIncidentEventPublisherWritesIncidentCreated();

    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t01-success"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("OPEN")))
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(
            jsonPath(
                "$.assignmentAccountIds",
                containsInAnyOrder(
                    ACCOUNT_PRECINCT_COMMANDER.toString(),
                    ACCOUNT_PRECINCT_CAR.toString(),
                    ACCOUNT_PRECINCT_TEAM.toString())));

    assertThat(count("\"incident\"", "source_incident_id = ?", SOURCE_INCIDENT_ID)).isEqualTo(1);
    assertThat(count("missing_person", "incident_id = ?", INCIDENT_ID.toString())).isEqualTo(1);
    assertThat(
            count(
                "incident_assignment",
                "incident_id = ? AND revoked_at IS NULL",
                INCIDENT_ID.toString()))
        .isEqualTo(3);
    assertThat(
            count(
                "operational_period",
                "incident_id = ? AND sequence_number = 1",
                INCIDENT_ID.toString()))
        .isEqualTo(1);
    assertThat(
            count(
                "event_dispatch_job",
                "incident_id = ? AND event_type = ? AND payload LIKE ?",
                INCIDENT_ID.toString(),
                "INCIDENT_CREATED",
                "%\"status\":\"OPEN\"%"))
        .isEqualTo(1);
    verify(referenceMarkerSeed)
        .createForIncident(
            eq(INCIDENT_ID),
            eq(List.of(new SeedMarker("CLUE", "MOCK_SEED", "신고자 진술 위치", 126.9565, 37.5712))));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("OP1 자동 생성 실패 시 import 트랜잭션 전체가 rollback된다")
  void op1CreationFailureRollsBackIncidentImportTransaction() throws Exception {
    givenMock112Incident(SOURCE_INCIDENT_ID);
    givenIncidentEventPublisherWritesIncidentCreated();
    when(initialOperationalPeriodCreator.createOp1(any(UUID.class)))
        .thenThrow(new IllegalStateException("op1_creation_failed"));

    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t01-op1-failure"))
        .andExpect(status().is5xxServerError());

    assertThat(count("\"incident\"", "source_incident_id = ?", SOURCE_INCIDENT_ID)).isZero();
    assertThat(count("missing_person", "1 = 1")).isZero();
    assertThat(count("incident_assignment", "1 = 1")).isZero();
    assertThat(count("operational_period", "1 = 1")).isZero();
    assertThat(count("event_dispatch_job", "event_type = ?", "INCIDENT_CREATED")).isZero();
    verifyNoInteractions(referenceMarkerSeed);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("초기 기준 마커 생성 실패 시 import 트랜잭션 전체가 rollback된다")
  void referenceMarkerSeedFailureRollsBackIncidentImportTransaction() throws Exception {
    givenMock112Incident(SOURCE_INCIDENT_ID);
    givenOp1CreatorWritesOp1();
    doThrow(new IllegalStateException("reference_marker_seed_failed"))
        .when(referenceMarkerSeed)
        .createForIncident(any(UUID.class), anyList());

    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t01-marker-failure"))
        .andExpect(status().is5xxServerError());

    assertThat(count("\"incident\"", "source_incident_id = ?", SOURCE_INCIDENT_ID)).isZero();
    assertThat(count("missing_person", "1 = 1")).isZero();
    assertThat(count("incident_assignment", "1 = 1")).isZero();
    assertThat(count("operational_period", "1 = 1")).isZero();
    assertThat(count("event_dispatch_job", "event_type = ?", "INCIDENT_CREATED")).isZero();
    verifyNoInteractions(incidentEventPublisher);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("같은 sourceIncidentId import 재호출은 기존 incident를 반환하고 중복 row를 만들지 않는다")
  void duplicateSourceIncidentImportReplaysExistingIncidentWithoutDuplicateRows() throws Exception {
    givenMock112Incident(SOURCE_INCIDENT_ID);
    givenOp1CreatorWritesOp1();
    givenIncidentEventPublisherWritesIncidentCreated();

    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t01-first"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t01-second"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.status", is("OPEN")))
        .andExpect(jsonPath("$.version", is(1)));

    assertThat(count("\"incident\"", "source_incident_id = ?", SOURCE_INCIDENT_ID)).isEqualTo(1);
    assertThat(
            count(
                "incident_assignment",
                "incident_id = ? AND revoked_at IS NULL",
                INCIDENT_ID.toString()))
        .isEqualTo(3);
    assertThat(
            count(
                "operational_period",
                "incident_id = ? AND sequence_number = 1",
                INCIDENT_ID.toString()))
        .isEqualTo(1);
    assertThat(count("event_dispatch_job", "event_type = ?", "INCIDENT_CREATED")).isEqualTo(1);
    verify(externalIncidentAdapter, times(1)).fetchIncident(SOURCE_INCIDENT_ID);
    verify(initialOperationalPeriodCreator, times(1)).createOp1(INCIDENT_ID);
    verify(referenceMarkerSeed, times(1))
        .createForIncident(
            eq(INCIDENT_ID),
            eq(List.of(new SeedMarker("CLUE", "MOCK_SEED", "신고자 진술 위치", 126.9565, 37.5712))));
    verify(incidentEventPublisher, times(1))
        .publishIncidentCreated(any(IncidentCreatedEvent.class));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("CLOSED 사건의 같은 sourceIncidentId import 재호출은 incident_closed로 거부한다")
  void closedSourceIncidentImportRejectedWithIncidentClosed() throws Exception {
    givenClosedIncident(SOURCE_INCIDENT_ID);

    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t02-closed-import"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("incident_closed")));

    assertThat(count("\"incident\"", "source_incident_id = ?", SOURCE_INCIDENT_ID)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM \"incident\" WHERE id = ?",
                String.class,
                INCIDENT_ID.toString()))
        .isEqualTo("CLOSED");
    assertThat(count("incident_assignment", "1 = 1")).isZero();
    assertThat(count("operational_period", "1 = 1")).isZero();
    assertThat(count("event_dispatch_job", "1 = 1")).isZero();
    assertThat(count("idempotency_record", "1 = 1")).isZero();
    verifyNoInteractions(
        externalIncidentAdapter,
        initialOperationalPeriodCreator,
        referenceMarkerSeed,
        incidentEventPublisher);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("같은 Idempotency-Key의 다른 body는 idempotency_mismatch로 거부한다")
  void sameIdempotencyKeyWithDifferentBodyRejected() throws Exception {
    givenMock112Incident(SOURCE_INCIDENT_ID);
    givenOp1CreatorWritesOp1();
    givenIncidentEventPublisherWritesIncidentCreated();

    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t01-mismatch"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(importRequest("mock-112-incident-002", "idem-l1-t01-mismatch"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("idempotency_mismatch")));

    assertThat(count("\"incident\"", "1 = 1")).isEqualTo(1);
    verify(externalIncidentAdapter, times(1)).fetchIncident(SOURCE_INCIDENT_ID);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.APP,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("APP 채널 import 요청은 channel_not_allowed로 거부한다")
  void appChannelImportRejected() throws Exception {
    mockMvc
        .perform(importRequestWithChannel(SOURCE_INCIDENT_ID, "idem-l1-t01-app", "APP"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(
        externalIncidentAdapter,
        initialOperationalPeriodCreator,
        referenceMarkerSeed,
        incidentEventPublisher);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.SUPPORT_UNIT,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("지원부대 FIELD_COMMANDER import 요청은 role_denied로 거부한다")
  void supportUnitFieldCommanderImportRejected() throws Exception {
    mockMvc
        .perform(importRequest(SOURCE_INCIDENT_ID, "idem-l1-t01-support"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("role_denied")));

    verifyNoInteractions(
        externalIncidentAdapter,
        initialOperationalPeriodCreator,
        referenceMarkerSeed,
        incidentEventPublisher);
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.WEB,
      accountId = "11111111-1111-1111-1111-111111110001",
      roles = {Role.FIELD_COMMANDER})
  @DisplayName("Idempotency-Key 없는 import 요청은 write_conflict로 거부한다")
  void missingIdempotencyKeyRejected() throws Exception {
    mockMvc
        .perform(importRequestWithoutIdempotencyKey(SOURCE_INCIDENT_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));

    verifyNoInteractions(
        externalIncidentAdapter,
        initialOperationalPeriodCreator,
        referenceMarkerSeed,
        incidentEventPublisher);
  }

  private org.springframework.test.web.servlet.RequestBuilder importRequest(
      String sourceIncidentId, String idempotencyKey) {
    return importRequestWithChannel(sourceIncidentId, idempotencyKey, "WEB");
  }

  private org.springframework.test.web.servlet.RequestBuilder importRequestWithChannel(
      String sourceIncidentId, String idempotencyKey, String clientChannel) {
    return post("/api/incidents/import")
        .header("Authorization", "Bearer test-web")
        .header("X-Client-Channel", clientChannel)
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"sourceIncidentId\":\"" + sourceIncidentId + "\"}");
  }

  private org.springframework.test.web.servlet.RequestBuilder importRequestWithoutIdempotencyKey(
      String sourceIncidentId) {
    return post("/api/incidents/import")
        .header("Authorization", "Bearer test-web")
        .header("X-Client-Channel", "WEB")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"sourceIncidentId\":\"" + sourceIncidentId + "\"}");
  }

  private void givenMock112Incident(String sourceIncidentId) {
    when(externalIncidentAdapter.fetchIncident(sourceIncidentId))
        .thenReturn(
            new ExternalIncident(
                sourceIncidentId,
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
                    new ExternalAssignment(
                        "mock-112-incident-001:precinct-cmd",
                        ACCOUNT_PRECINCT_COMMANDER.toString(),
                        "FIELD_COMMANDER",
                        OffsetDateTime.parse("2026-04-28T00:00:00Z")),
                    new ExternalAssignment(
                        "mock-112-incident-001:precinct-car",
                        ACCOUNT_PRECINCT_CAR.toString(),
                        "MEMBER",
                        OffsetDateTime.parse("2026-04-28T00:00:00Z")),
                    new ExternalAssignment(
                        "mock-112-incident-001:precinct-team",
                        ACCOUNT_PRECINCT_TEAM.toString(),
                        "MEMBER",
                        OffsetDateTime.parse("2026-04-28T00:00:00Z"))),
                List.of(
                    new ExternalSeedMarker("CLUE", "MOCK_SEED", "신고자 진술 위치", 126.9565, 37.5712))));
  }

  private void givenClosedIncident(String sourceIncidentId) {
    jdbc.update(
        """
        INSERT INTO "incident" (
          id, source_incident_id, title, status, opened_at, closed_at,
          closed_by_account_id, version, created_at, updated_at
        ) VALUES (?, ?, ?, 'CLOSED', ?, ?, ?, 3, ?, ?)
        """,
        INCIDENT_ID.toString(),
        sourceIncidentId,
        "인왕산 북측 산책로 실종 신고",
        Instant.parse("2026-04-28T00:00:00Z"),
        Instant.parse("2026-04-28T03:00:00Z"),
        ACCOUNT_PRECINCT_COMMANDER.toString(),
        Instant.parse("2026-04-28T00:00:00Z"),
        Instant.parse("2026-04-28T03:00:00Z"));
  }

  private void givenOp1CreatorWritesOp1() {
    when(initialOperationalPeriodCreator.createOp1(any(UUID.class)))
        .thenAnswer(
            invocation -> {
              UUID incidentId = invocation.getArgument(0);
              jdbc.update(
                  """
                  INSERT INTO operational_period (
                    id, incident_id, sequence_number, status, reason, reason_memo,
                    started_by_account_id, ended_by_account_id, started_at, ended_at,
                    version, created_at, updated_at
                  ) VALUES (?, ?, 1, 'ACTIVE', 'INITIAL', NULL, NULL, NULL, ?, NULL, 1, ?, ?)
                  """,
                  OP1_ID.toString(),
                  incidentId.toString(),
                  Instant.parse("2026-04-28T00:00:00Z"),
                  Instant.parse("2026-04-28T00:00:00Z"),
                  Instant.parse("2026-04-28T00:00:00Z"));
              return new InitialOperationalPeriodResult(
                  new OperationalPeriodRow(
                      OP1_ID,
                      incidentId,
                      "ACTIVE",
                      1,
                      Instant.parse("2026-04-28T00:00:00Z"),
                      null,
                      "INITIAL",
                      1L));
            });
  }

  private void givenIncidentEventPublisherWritesIncidentCreated() {
    doAnswer(
            invocation -> {
              IncidentCreatedEvent event = invocation.getArgument(0);
              Instant occurredAt = Instant.parse("2026-04-28T00:00:00Z");
              String eventId = "evt-incident-created-" + event.id() + "-v" + event.version();
              String payload =
                  "{\"id\":\""
                      + event.id()
                      + "\",\"status\":\""
                      + event.status()
                      + "\",\"version\":"
                      + event.version()
                      + ",\"sourceIncidentId\":\""
                      + event.sourceIncidentId()
                      + "\"}";
              jdbc.update(
                  """
                  INSERT INTO event_dispatch_job (
                    id, event_id, incident_id, event_type, payload_format_version, payload,
                    source_entity_type, source_entity_id, occurred_at, dispatch_status,
                    created_at, updated_at
                  ) VALUES (?, ?, ?, 'INCIDENT_CREATED', 1, ?, 'incident', ?, ?, 'PENDING', ?, ?)
                  """,
                  UUID.nameUUIDFromBytes(
                          ("event-dispatch-job:" + eventId).getBytes(StandardCharsets.UTF_8))
                      .toString(),
                  eventId,
                  event.id().toString(),
                  payload,
                  event.id().toString(),
                  occurredAt,
                  occurredAt,
                  occurredAt);
              return null;
            })
        .when(incidentEventPublisher)
        .publishIncidentCreated(any(IncidentCreatedEvent.class));
  }

  private int count(String table, String whereClause, Object... args) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause, Integer.class, args);
    return count == null ? 0 : count;
  }
}
