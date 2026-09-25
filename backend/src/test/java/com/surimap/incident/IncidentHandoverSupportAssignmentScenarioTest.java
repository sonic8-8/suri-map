package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.board.BoardAssembler;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardSourceRow;
import com.surimap.client.fcm.MockFcmDispatcher;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.stream.SseReplayEventStore.ReplayAppend;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.mock112.AssignmentPollingHandler;
import com.surimap.incident.service.IncidentAssignmentView;
import com.surimap.incident.service.IncidentAssignmentView.NotificationTargets;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.marker.notification.fixture.NotificationFixtures;
import com.surimap.support.auth.WithMockAccount;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 사건 인계·지원 배정의 DB 저장, 이벤트 저장과 FCM 호출을 확인한다. SSE 전달과 상황판 조립은 테스트에서 직접 호출하므로 자동 전달이나 화면 표시를 검증하지는
 * 않는다.
 */
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "fcm.provider=mock")
class IncidentHandoverSupportAssignmentScenarioTest extends PostGisIntegrationTestSupport {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SOURCE_INCIDENT_ID = "00000000-0000-0000-0000-000000000001";
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final String PATH_CAR_ID = "path-precinct-car-001";
  private static final String PATH_FOOT_ID = "path-precinct-foot-001";
  private static final String MARKER_ID = "mk-precinct-clue-001";
  private static final String MEMO_ID = "memo-precinct-handover-001";
  private static final String HANDOVER_STATUS_ID = "handover-status-inc-precinct-first-001";

  @Autowired private AssignmentPollingHandler assignmentPollingHandler;

  @Autowired private IncidentAssignmentView incidentAssignmentView;

  @Autowired private SseStreamService sseStreamService;

  @Autowired private MockFcmDispatcher fcmDispatcher;

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void useMainMigrationsWithAccountFixtures(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.flyway.locations", () -> "classpath:db/migration-test,classpath:db/migration");
  }

  @BeforeEach
  void seedIncidentBeforeHandover() {
    assignmentPollingHandler.reset();
    fcmDispatcher.reset();
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE fcm_token, marker_notification, photo, marker, event_dispatch_job,
          operational_period, incident_assignment, missing_person, idempotency_record,
          "incident" RESTART IDENTITY CASCADE
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS search_path_seed_probe (
          id VARCHAR(80) PRIMARY KEY,
          incident_id UUID NOT NULL,
          op_id UUID NOT NULL
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS marker_seed_probe (
          id VARCHAR(80) PRIMARY KEY,
          incident_id UUID NOT NULL,
          op_id UUID NOT NULL
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS handover_memo_seed_probe (
          id VARCHAR(80) PRIMARY KEY,
          incident_id UUID NOT NULL,
          op_id UUID NOT NULL
        )
        """);
    jdbcTemplate.execute(
        "TRUNCATE TABLE handover_memo_seed_probe, marker_seed_probe, search_path_seed_probe");
    seedAccountAndPolicePhoneFixtures();
    seedActiveFcmTokens();
    seedIncident();
    seedOp1Evidence();
    seedInitialAssignments();
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.SUPPORT_UNIT,
      channel = Channel.APP,
      accountId = "acct-support-team",
      policePhoneId = "00000000-0000-0000-0000-000000000208",
      roles = {Role.MEMBER})
  @DisplayName("인계·지원 배정을 반영할 경우, 기존 배정을 유지하고 신규 업무폰에 알린다")
  void handoverAndSupportAssignments_preserveAssignmentsAndNotifyNewPolicePhones()
      throws Exception {
    // given: 초동 배정·첫 수색 차수의 자료와 현장 업무폰의 FCM 토큰이 있는 사건이다.
    assignmentPollingHandler.registerInitialAssignments(initialAssignments());
    assertThat(count("marker_notification", "1 = 1")).isZero();

    // when: 실종팀 인계에 이어 지원 부대 배정을 반영한다.
    assignmentPollingHandler.handleAssignmentChanges(SOURCE_INCIDENT_ID, handoverAssignments());
    assignmentPollingHandler.handleAssignmentChanges(SOURCE_INCIDENT_ID, supportAssignments());

    // then: 기존 배정을 해제하지 않고 신규 배정만 추가한다.
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
    assertThat(
            count("incident_assignment", "incident_id = ? AND revoked_at IS NOT NULL", INCIDENT_ID))
        .isZero();
    assertOp1ProbeRowsUnchanged();

    // when: 같은 배정 목록을 다시 수신한다.
    assignmentPollingHandler.handleAssignmentChanges(
        SOURCE_INCIDENT_ID,
        concat(initialAssignments(), handoverAssignments(), supportAssignments()));

    // then: 배정과 이벤트를 중복 저장하지 않는다.
    List<AssignmentChangedOutboxRow> assignmentEvents = assignmentChangedOutboxRows();
    assertThat(assignmentEvents).hasSize(2);
    assertThat(count("incident_assignment", "incident_id = ? AND revoked_at IS NULL", INCIDENT_ID))
        .isEqualTo(8);

    AssignmentChangedOutboxRow handoverEvent = assignmentEvents.get(0);
    assertThat(handoverEvent.payloadStringList("changedAccountIds"))
        .containsExactly(
            "11111111-1111-1111-1111-111111110004", "11111111-1111-1111-1111-111111110005");

    AssignmentChangedOutboxRow supportEvent = assignmentEvents.get(1);
    assertThat(supportEvent.eventType()).isEqualTo("INCIDENT_ASSIGNMENT_CHANGED");
    assertThat(supportEvent.dispatchStatus()).isEqualTo("PENDING");
    assertThat(supportEvent.sourceEntityType()).isEqualTo("incident_assignment");
    assertThat(supportEvent.sourceEntityId()).isEqualTo(INCIDENT_ID);
    assertThat(supportEvent.payloadString("status")).isEqualTo("ACTIVE");
    assertThat(supportEvent.payloadVersion()).isEqualTo(3L);
    assertThat(supportEvent.payloadStringList("changedAccountIds"))
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_CHANGED_ACCOUNT_IDS);

    // when: 저장된 이벤트를 SSE 스트림 서비스에 직접 전달한다.
    ReplayAppend sseEvidence =
        sseStreamService.dispatchLive(supportEvent.rowId(), supportEvent.toPublishRequest(), 1L);
    // then: 전달한 배정 이벤트의 종류와 버전을 유지한다.
    assertThat(sseEvidence.isNew()).isTrue();
    assertThat(sseEvidence.event().envelope().type()).isEqualTo("INCIDENT_ASSIGNMENT_CHANGED");
    assertThat(sseEvidence.event().envelope().payload().get("version")).isEqualTo(3);

    NotificationTargets targets =
        incidentAssignmentView.notificationTargets(INCIDENT_ID, "SUPPORT_ASSIGNMENT");
    assertThat(targets.accountIds())
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_RECIPIENT_ACCOUNT_IDS);
    assertThat(targets.policePhoneIds())
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_RECIPIENT_POLICE_PHONE_IDS);

    // then: 실제 배정 서비스가 호출한 FCM 기록을 확인한다. 테스트에서 send를 호출하지 않는다.
    MockFcmDispatcher.CapturedDispatch captured =
        fcmDispatcher
            .findByEventId("fcm:INCIDENT_ASSIGNMENT_CHANGED:" + INCIDENT_ID + ":v3")
            .orElseThrow();

    assertThat(fcmDispatcher.getDispatchCount()).isEqualTo(2);
    assertThat(
            fcmDispatcher
                .findByEventId("fcm:INCIDENT_ASSIGNMENT_CHANGED:" + INCIDENT_ID + ":v2")
                .orElseThrow()
                .recipients())
        .containsExactly("fcm:dev-alpha-phone-01");
    assertThat(captured.recipients())
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_FCM_RECIPIENTS);
    assertThat(captured.recipientPolicePhoneIds())
        .containsExactlyElementsOf(NotificationFixtures.ASSIGNMENT_RECIPIENT_POLICE_PHONE_IDS);
    assertThat(captured.payload())
        .containsEntry("type", "INCIDENT_ASSIGNMENT_CHANGED")
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("status", "ACTIVE")
        .containsEntry("version", 3L);
    assertThat(captured.payload())
        .doesNotContainKeys(
            "missingPersonName",
            "missingPersonPhone",
            "residentRegistrationNumber",
            "guardianName",
            "guardianPhone",
            "address",
            "photoUrl");
    assertThat(count("marker_notification", "1 = 1")).isZero();

    // when: 상황판 조회를 대신하지 않고, 고정 자료로 상황판 조립만 호출한다.
    BoardDTO board = assembleBoardFromFixtureRows(supportEvent);
    // then: 조립 결과에 첫 수색 차수와 인계·배정 상태가 유지된다.
    assertThat(board.boardResponseVersion()).isGreaterThanOrEqualTo(supportEvent.payloadVersion());
    assertThat(board.slotRow("path", PATH_CAR_ID).payload())
        .containsEntry("opId", OP1_ID.toString());
    assertThat(board.slotRow("path", PATH_FOOT_ID).payload())
        .containsEntry("opId", OP1_ID.toString());
    assertThat(board.slotRow("marker", MARKER_ID).payload())
        .containsEntry("opId", OP1_ID.toString());
    assertThat(board.slotRow("op_history", OP1_ID.toString()).payload())
        .containsEntry("handoverState", "initial_op1_preserved");
    assertThat(board.slotRow("handover_status", HANDOVER_STATUS_ID).payload())
        .containsEntry("handoverCompleted", true)
        .containsEntry("supportAssigned", true);

    // when/then: 지원 부대 계정으로 사건을 조회하면 반영된 배정 목록을 받는다.
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
  }

  private void seedIncident() {
    OffsetDateTime openedAt = OffsetDateTime.parse("2026-04-28T09:00:00+09:00");
    jdbcTemplate.update(
        """
        INSERT INTO "incident" (
          id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id,
          version, created_at, updated_at
        )
        VALUES (?, ?::uuid, '광주 무등산 실종 신고', 'OPEN', ?, NULL, NULL, 1, ?, ?)
        """,
        INCIDENT_ID,
        SOURCE_INCIDENT_ID,
        openedAt,
        openedAt,
        openedAt);
    jdbcTemplate.update(
        """
        INSERT INTO missing_person (
          incident_id, display_name, photo_object_key, appearance_text,
          last_seen_location_text, last_seen_at, imported_at
        )
        VALUES (?, '가상 실종자 001', 'mock-112/missing-person/mock-112-incident-001.jpg',
          '남색 점퍼, 회색 등산화', '무등산 서측 탐방로 입구', ?, ?)
        """,
        INCIDENT_ID,
        OffsetDateTime.parse("2026-04-28T08:30:00+09:00"),
        openedAt);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_at, version, created_at, updated_at
        )
        VALUES (?, ?, 1, 'ACTIVE', 'INITIAL', ?, 1, ?, ?)
        """,
        OP1_ID,
        INCIDENT_ID,
        openedAt,
        openedAt,
        openedAt);
  }

  private void seedAccountAndPolicePhoneFixtures() {
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS account (
          id UUID PRIMARY KEY,
          login_id VARCHAR(64) NOT NULL UNIQUE,
          password_hash VARCHAR(255) NOT NULL,
          display_name VARCHAR(128) NOT NULL,
          account_type VARCHAR(32) NOT NULL,
          organization_type VARCHAR(32) NOT NULL,
          status VARCHAR(32) NOT NULL,
          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS police_phone (
          id VARCHAR(64) PRIMARY KEY,
          phone_code VARCHAR(64) NOT NULL UNIQUE,
          display_name VARCHAR(128) NOT NULL,
          account_id UUID NOT NULL,
          status VARCHAR(32) NOT NULL,
          last_heartbeat_at TIMESTAMP WITH TIME ZONE,
          last_sync_at TIMESTAMP WITH TIME ZONE,
          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
          CONSTRAINT fk_police_phone_account FOREIGN KEY (account_id) REFERENCES account(id)
        )
        """);
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status
        )
        VALUES
          ('11111111-1111-1111-1111-111111110001', 'acct-precinct-cmd', '{noop}fixture',
            '종로 지구대 지휘', 'COMMAND', 'POLICE_SUBSTATION', 'ACTIVE'),
          ('11111111-1111-1111-1111-111111110002', 'acct-precinct-car', '{noop}fixture',
            '종로 지구대 순찰차', 'PATROL_CAR', 'POLICE_SUBSTATION', 'ACTIVE'),
          ('11111111-1111-1111-1111-111111110003', 'acct-precinct-team', '{noop}fixture',
            '종로 지구대 팀', 'TEAM', 'POLICE_SUBSTATION', 'ACTIVE'),
          ('11111111-1111-1111-1111-111111110004', 'acct-cmd-alpha', '{noop}fixture',
            '실종팀 알파 지휘', 'COMMAND', 'MISSING_TEAM', 'ACTIVE'),
          ('11111111-1111-1111-1111-111111110005', 'acct-team-alpha', '{noop}fixture',
            '실종팀 알파 팀', 'TEAM', 'MISSING_TEAM', 'ACTIVE'),
          ('11111111-1111-1111-1111-111111110006', 'acct-support-cmd', '{noop}fixture',
            '지원 브라보 지휘', 'COMMAND', 'SUPPORT_UNIT', 'ACTIVE'),
          ('11111111-1111-1111-1111-111111110007', 'acct-support-car', '{noop}fixture',
            '지원 브라보 순찰차', 'PATROL_CAR', 'SUPPORT_UNIT', 'ACTIVE'),
          ('11111111-1111-1111-1111-111111110008', 'acct-support-team', '{noop}fixture',
            '지원 브라보 팀', 'TEAM', 'SUPPORT_UNIT', 'ACTIVE')
        ON CONFLICT (id) DO NOTHING
        """);
    jdbcTemplate.update(
        """
        INSERT INTO police_phone (id, phone_code, display_name, account_id, status)
        VALUES
          ('00000000-0000-0000-0000-000000000201', 'dev-precinct-cmd-phone-01',
            '종로 지구대 지휘 폴리폰', '11111111-1111-1111-1111-111111110001', 'ACTIVE'),
          ('50000000-0000-0000-0000-000000000001', 'dev-precinct-car-01',
            '종로 지구대 순찰차 폴리폰', '11111111-1111-1111-1111-111111110002', 'ACTIVE'),
          ('00000000-0000-0000-0000-000000000101', 'dev-precinct-phone-01',
            '종로 지구대 팀 폴리폰', '11111111-1111-1111-1111-111111110003', 'ACTIVE'),
          ('00000000-0000-0000-0000-000000000204', 'dev-alpha-cmd-phone-01',
            '실종팀 알파 지휘 폴리폰', '11111111-1111-1111-1111-111111110004', 'ACTIVE'),
          ('00000000-0000-0000-0000-000000000205', 'dev-alpha-phone-01',
            '실종팀 알파 폴리폰', '11111111-1111-1111-1111-111111110005', 'ACTIVE'),
          ('00000000-0000-0000-0000-000000000206', 'dev-support-cmd-phone-01',
            '지원 브라보 지휘 폴리폰', '11111111-1111-1111-1111-111111110006', 'ACTIVE'),
          ('00000000-0000-0000-0000-000000000207', 'dev-support-car-01',
            '지원 브라보 순찰차 폴리폰', '11111111-1111-1111-1111-111111110007', 'ACTIVE'),
          ('00000000-0000-0000-0000-000000000208', 'dev-support-phone-01',
            '지원 브라보 팀 폴리폰', '11111111-1111-1111-1111-111111110008', 'ACTIVE')
        ON CONFLICT (id) DO NOTHING
        """);
  }

  private void seedOp1Evidence() {
    jdbcTemplate.update(
        "INSERT INTO search_path_seed_probe (id, incident_id, op_id) VALUES (?, ?, ?)",
        PATH_CAR_ID,
        INCIDENT_ID,
        OP1_ID);
    jdbcTemplate.update(
        "INSERT INTO search_path_seed_probe (id, incident_id, op_id) VALUES (?, ?, ?)",
        PATH_FOOT_ID,
        INCIDENT_ID,
        OP1_ID);
    jdbcTemplate.update(
        "INSERT INTO marker_seed_probe (id, incident_id, op_id) VALUES (?, ?, ?)",
        MARKER_ID,
        INCIDENT_ID,
        OP1_ID);
    jdbcTemplate.update(
        "INSERT INTO handover_memo_seed_probe (id, incident_id, op_id) VALUES (?, ?, ?)",
        MEMO_ID,
        INCIDENT_ID,
        OP1_ID);
  }

  private void seedActiveFcmTokens() {
    // 기존 배정 업무폰과 신규 현장 업무폰의 토큰을 준비한다. 지휘 업무폰은 토큰이 없는 fixture다.
    // 계정 유형만으로 FCM 수신을 제한한다는 의미는 아니다.
    jdbcTemplate.update(
        """
        INSERT INTO fcm_token (
          id, account_id, police_phone_id, app_instance_id, token_hash, token_ciphertext, status
        )
        SELECT id, account_id, id, 'incident-assignment-test', phone_code,
          'cipher:fcm:' || phone_code, 'ACTIVE'
        FROM police_phone
        WHERE id IN (
          '00000000-0000-0000-0000-000000000101',
          '00000000-0000-0000-0000-000000000205',
          '00000000-0000-0000-0000-000000000207',
          '00000000-0000-0000-0000-000000000208'
        )
        """);
  }

  private void seedInitialAssignments() {
    insertAssignment(
        "10000000-0000-4000-8000-000000000001",
        "11111111-1111-1111-1111-111111110001",
        "FIELD_COMMANDER");
    insertAssignment(
        "10000000-0000-4000-8000-000000000002", "11111111-1111-1111-1111-111111110002", "MEMBER");
    insertAssignment(
        "10000000-0000-4000-8000-000000000003", "11111111-1111-1111-1111-111111110003", "MEMBER");
  }

  private void insertAssignment(String id, String accountId, String incidentRole) {
    OffsetDateTime assignedAt = OffsetDateTime.parse("2026-04-28T09:00:00+09:00");
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
        """,
        UUID.fromString(id),
        INCIDENT_ID,
        UUID.fromString(accountId),
        incidentRole,
        assignedAt,
        assignedAt,
        assignedAt);
  }

  private List<String> activeAssignmentAccountIds() {
    return jdbcTemplate.queryForList(
        """
        SELECT account_id
        FROM incident_assignment
        WHERE incident_id = ?
          AND revoked_at IS NULL
        ORDER BY assigned_at ASC, account_id ASC
        """,
        String.class,
        INCIDENT_ID);
  }

  // ponytail: 전용 probe 행의 보존만 확인한다. 실제 경로·마커·메모 보존은 해당 저장 흐름의 테스트로 대체한다.
  private void assertOp1ProbeRowsUnchanged() {
    assertThat(evidenceCount("search_path_seed_probe", PATH_CAR_ID)).isEqualTo(1);
    assertThat(evidenceCount("search_path_seed_probe", PATH_FOOT_ID)).isEqualTo(1);
    assertThat(evidenceCount("marker_seed_probe", MARKER_ID)).isEqualTo(1);
    assertThat(evidenceCount("handover_memo_seed_probe", MEMO_ID)).isEqualTo(1);
  }

  private int evidenceCount(String tableName, String id) {
    return count(tableName, "id = ? AND incident_id = ? AND op_id = ?", id, INCIDENT_ID, OP1_ID);
  }

  private List<AssignmentChangedOutboxRow> assignmentChangedOutboxRows() {
    return jdbcTemplate.query(
        """
        SELECT
          id,
          event_id,
          incident_id,
          event_type,
          payload_format_version,
          payload::text AS payload_json,
          source_entity_type,
          source_entity_id,
          occurred_at,
          dispatch_status
        FROM event_dispatch_job
        WHERE incident_id = ?
          AND event_type = 'INCIDENT_ASSIGNMENT_CHANGED'
        ORDER BY (payload ->> 'version')::bigint ASC
        """,
        (rs, rowNum) ->
            new AssignmentChangedOutboxRow(
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getObject("incident_id", UUID.class),
                rs.getString("event_type"),
                rs.getInt("payload_format_version"),
                readPayload(rs.getString("payload_json")),
                rs.getString("source_entity_type"),
                rs.getObject("source_entity_id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getString("dispatch_status")),
        INCIDENT_ID);
  }

  private Map<String, Object> readPayload(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("event payload must be valid JSON", exception);
    }
  }

  private BoardDTO assembleBoardFromFixtureRows(AssignmentChangedOutboxRow supportEvent) {
    long version = supportEvent.payloadVersion();
    String eventId = supportEvent.eventId().toString();
    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                INCIDENT_ID.toString(),
                "board-sc02-support-assignment-001",
                0L,
                OffsetDateTime.parse("2026-04-28T11:00:05+09:00"),
                OP1_ID.toString(),
                List.of(OP1_ID.toString()),
                "overall-area-hash-precinct-current",
                List.of(
                    sourceRow(
                        "path",
                        "S3-1",
                        PATH_CAR_ID,
                        "board-path-precinct-car-001",
                        "ACTIVE",
                        version,
                        1L,
                        eventId,
                        "hash-path-precinct-car-001",
                        Map.of("incidentId", INCIDENT_ID.toString(), "opId", OP1_ID.toString())),
                    sourceRow(
                        "path",
                        "S3-1",
                        PATH_FOOT_ID,
                        "board-path-precinct-foot-001",
                        "ACTIVE",
                        version,
                        1L,
                        eventId,
                        "hash-path-precinct-foot-001",
                        Map.of("incidentId", INCIDENT_ID.toString(), "opId", OP1_ID.toString())),
                    sourceRow(
                        "marker",
                        "S5",
                        MARKER_ID,
                        "board-marker-precinct-clue-001",
                        "ACTIVE",
                        version,
                        1L,
                        eventId,
                        "hash-marker-precinct-clue-001",
                        Map.of("incidentId", INCIDENT_ID.toString(), "opId", OP1_ID.toString())),
                    sourceRow(
                        "op_history",
                        "S8",
                        OP1_ID.toString(),
                        "board-op-precinct-001-op1",
                        "ACTIVE",
                        version,
                        1L,
                        eventId,
                        "hash-op-precinct-001-op1",
                        Map.of(
                            "incidentId",
                            INCIDENT_ID.toString(),
                            "opId",
                            OP1_ID.toString(),
                            "handoverState",
                            "initial_op1_preserved")),
                    sourceRow(
                        "handover_status",
                        "S8",
                        HANDOVER_STATUS_ID,
                        "board-handover-status-inc-precinct-first-001",
                        "COMPLETED",
                        version,
                        1L,
                        eventId,
                        "hash-handover-status-inc-precinct-first-001",
                        Map.ofEntries(
                            Map.entry("incidentId", INCIDENT_ID.toString()),
                            Map.entry("opId", OP1_ID.toString()),
                            Map.entry("handoverCompleted", true),
                            Map.entry("supportAssigned", true))))));
  }

  private static BoardSourceRow sourceRow(
      String slot,
      String sourceSpec,
      String sourceResponseId,
      String boardRowId,
      String status,
      long version,
      long sequence,
      String latestEventId,
      String sourceHash,
      Map<String, Object> payload) {
    return new BoardSourceRow(
        slot,
        sourceSpec,
        sourceResponseId,
        boardRowId,
        status,
        version,
        sequence,
        latestEventId,
        sourceHash,
        payload);
  }

  private int count(String table, String whereClause, Object... args) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause, Integer.class, args);
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
    List<T> merged = new ArrayList<>();
    for (List<T> value : values) {
      merged.addAll(value);
    }
    return List.copyOf(merged);
  }

  private record AssignmentChangedOutboxRow(
      UUID rowId,
      UUID eventId,
      UUID incidentId,
      String eventType,
      int payloadFormatVersion,
      Map<String, Object> payload,
      String sourceEntityType,
      UUID sourceEntityId,
      Instant occurredAt,
      String dispatchStatus) {

    PublishRequest toPublishRequest() {
      return new PublishRequest(
          eventId,
          incidentId,
          eventType,
          payloadFormatVersion,
          sourceEntityType,
          sourceEntityId,
          occurredAt,
          payload);
    }

    long payloadVersion() {
      Object version = payload.get("version");
      assertThat(version).isInstanceOf(Number.class);
      return ((Number) version).longValue();
    }

    String payloadString(String key) {
      return String.valueOf(payload.get(key));
    }

    List<String> payloadStringList(String key) {
      Object value = payload.get(key);
      assertThat(value).isInstanceOf(List.class);
      return ((List<?>) value).stream().map(String::valueOf).toList();
    }
  }

  @TestConfiguration
  static class SearchAreaFixtureConfig {

    @Bean
    SearchAreaQuery searchAreaQuery() {
      return new SearchAreaQueryMock();
    }
  }
}
