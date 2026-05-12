package com.surimap.harness.sc12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.board.BoardAssembler;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardSourceRow;
import com.surimap.board.PackageBadgeBoardAssembler;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.stream.SseReplayEventStore;
import com.surimap.eventhub.stream.SseReplayEventStore.ReplayAppend;
import com.surimap.eventhub.stream.SseStreamService;
import com.surimap.external.ExternalAssignment;
import com.surimap.external.mock112.AssignmentPollingHandler;
import com.surimap.incident.event.IncidentClosedEvent;
import com.surimap.incident.lifecycle.IncidentLifecycleGuardException;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.offlinepackage.query.OfflinePackageInstallationQuery;
import com.surimap.retention.purge.IncidentClosedPurgeHandler;
import com.surimap.retention.purge.IncidentDataPurgeRun;
import com.surimap.retention.purge.IncidentDataPurgeStatus;
import com.surimap.retention.purge.LocalPurgeState;
import com.surimap.retention.purge.PurgeCoordinator;
import com.surimap.retention.purge.PurgeHook;
import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookResult;
import com.surimap.support.auth.WithMockAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** SC-12 사건 종료가 close, purge handoff, SSE, sanitized board evidence로 수렴하는지 검증한다. */
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("L1-I03 SC-12 사건 종료·데이터 파기 통합 검증")
class Sc12IncidentCloseDataPurgeIntegrationTest extends PostGisIntegrationTestSupport {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final String SOURCE_INCIDENT_ID = "inc-precinct-first-001";
  private static final String OP1_ID = "op-precinct-001-op1";
  private static final String COMMANDER_ACCOUNT_ID = "11111111-1111-1111-1111-111111110004";
  private static final String TERMINAL_BOARD_ROW_ID =
      "board-incident-terminal-inc-precinct-first-001";
  private static final String TERMINAL_SOURCE_RESPONSE_ID = "tombstone-inc-precinct-first-001";
  private static final String PACKAGE_MANIFEST_ID = "tile-manifest-sc12-uuid-001";
  private static final Instant CLOSED_AT = Instant.parse("2026-04-28T01:45:00Z");
  private static final OffsetDateTime OPENED_AT = OffsetDateTime.parse("2026-04-28T09:00:00+09:00");
  private static final OffsetDateTime SERVER_TS = OffsetDateTime.parse("2026-04-28T10:45:05+09:00");

  @Autowired private AssignmentPollingHandler assignmentPollingHandler;

  @Autowired private IncidentClosedPurgeHandler incidentClosedPurgeHandler;

  @Autowired private OfflinePackageInstallationQuery offlinePackageInstallationQuery;

  @Autowired private MockMvc mockMvc;

  @Autowired private PurgeCoordinator purgeCoordinator;

  @Autowired private SseReplayEventStore sseReplayEventStore;

  @Autowired private SseStreamService sseStreamService;

  @DynamicPropertySource
  static void useMainMigrationsWithAccountFixtures(DynamicPropertyRegistry registry) {
    // SC-12는 닫기 API 보호 로직까지 타므로 운영 Flyway 테이블과 테스트 계정 fixture가 모두 필요하다.
    registry.add(
        "spring.flyway.locations", () -> "classpath:db/migration-test,classpath:db/migration");
  }

  @BeforeEach
  void seedSc12Incident() {
    assignmentPollingHandler.reset();
    sseReplayEventStore.clear();
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE marker_notification, photo, marker, offline_package_installation,
          offline_package_manifest, incident_data_purge_hook_step, incident_data_purge,
          event_dispatch_job, operational_period, incident_assignment, missing_person,
          idempotency_record, "incident" RESTART IDENTITY CASCADE
        """);
    seedIncident();
    seedMissingPerson();
    seedCommanderAssignment();
    seedOfflinePackageWithPii();
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      accountId = COMMANDER_ACCOUNT_ID,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName(
      "close 후 write-block, purge handoff, INCIDENT_CLOSED/PURGED SSE, terminal board가 수렴한다")
  void incidentCloseDataPurgeAndTerminalBoardConverge() throws Exception {
    // 1. 공개 웹 명령 경로로 닫기를 호출해 보호 로직, 멱등성, 응답 형태를 함께 검증한다.
    mockMvc
        .perform(
            post("/api/incidents/{incidentId}/close", INCIDENT_ID)
                .header("Authorization", "Bearer sc12-close")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-l1-i03-sc12-close")
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
        .andExpect(jsonPath("$.terminalSnapshot.status", is("CLOSED")))
        .andExpect(jsonPath("$.terminalSnapshot.displayName").doesNotExist())
        .andExpect(jsonPath("$.terminalSnapshot.photoObjectKey").doesNotExist())
        .andExpect(jsonPath("$.terminalSnapshot.lastSeenLocationText").doesNotExist());

    // 2. 닫기는 종료 상태이므로 활성 개인정보가 제거되고 이후 mock-112 배정은 거부되어야 한다.
    assertThat(count("missing_person", "incident_id = ?", INCIDENT_ID)).isZero();
    assertThatThrownBy(
            () ->
                assignmentPollingHandler.handleAssignmentChanges(
                    SOURCE_INCIDENT_ID, List.of(supportAssignment())))
        .isInstanceOf(IncidentLifecycleGuardException.class)
        .hasMessage("incident_closed");
    assertThat(activeAssignmentAccountIds())
        .doesNotContain("11111111-1111-1111-1111-111111110006");

    // 3. 종료 이벤트는 S4 전파와 파기 소비자로 넘어가는 인계 경계다.
    OutboxRow closedEvent = singleOutboxRow("INCIDENT_CLOSED");
    assertThat(closedEvent.sourceEntityType()).isEqualTo("incident");
    assertThat(closedEvent.sourceEntityId()).isEqualTo(INCIDENT_ID);
    assertThat(closedEvent.payload())
        .containsEntry("id", INCIDENT_ID.toString())
        .containsEntry("status", "CLOSED")
        .containsEntry("version", 2)
        .containsEntry("closedAt", "2026-04-28T01:45:00Z")
        .containsEntry("writeDisabledReason", "incident_closed");
    assertNoPii(closedEvent.payload());

    ReplayAppend closedSse =
        sseStreamService.dispatchLive(closedEvent.rowId(), closedEvent.toPublishRequest());
    assertThat(closedSse.isNew()).isTrue();
    assertThat(closedSse.event().envelope().type()).isEqualTo("INCIDENT_CLOSED");
    assertThat(closedSse.event().envelope().payload().get("closedAt"))
        .isEqualTo("2026-04-28T01:45:00Z");
    assertThat(sseReplayEventStore.terminalReplaySequence(INCIDENT_ID).orElseThrow())
        .isEqualTo(closedSse.replaySequence());

    // 4. INCIDENT_CLOSED를 소비한 뒤 로컬 파기를 실행해 SC-12가 파기 완료 종료 상태에 도달함을 증명한다.
    IncidentDataPurgeRun purgeRun =
        incidentClosedPurgeHandler.handle(
            new IncidentClosedEvent(
                INCIDENT_ID,
                payloadString(closedEvent, "status"),
                payloadLong(closedEvent, "version"),
                Instant.parse(payloadString(closedEvent, "closedAt")),
                payloadString(closedEvent, "writeDisabledReason")));
    assertThat(purgeRun.status()).isEqualTo(IncidentDataPurgeStatus.PENDING);
    assertThat(purgeRun.localPurgeState()).isEqualTo(LocalPurgeState.PURGE_PENDING);
    assertThat(singleString("SELECT status FROM incident_data_purge WHERE incident_id = ?"))
        .isEqualTo("PENDING");

    IncidentDataPurgeRun completed =
        purgeCoordinator.purgeIncident(INCIDENT_ID, purgeRun.purgeRunId());
    assertThat(completed.status()).isEqualTo(IncidentDataPurgeStatus.COMPLETED);
    assertThat(completed.localPurgeState()).isEqualTo(LocalPurgeState.LOCAL_PURGED);
    assertThat(packageStatuses()).containsOnly("PURGED");

    OutboxRow purgedEvent = singleOutboxRow("INCIDENT_PURGED");
    assertThat(purgedEvent.payload())
        .containsEntry("id", INCIDENT_ID.toString())
        .containsEntry("status", "PURGED")
        .containsEntry("version", Long.valueOf(completed.version()).intValue())
        .containsEntry("purgeRunId", completed.purgeRunId().toString());
    assertThat(purgedEvent.payload()).containsKey("purgedAt");
    assertNoPii(purgedEvent.payload());

    ReplayAppend purgedSse =
        sseStreamService.dispatchLive(purgedEvent.rowId(), purgedEvent.toPublishRequest());
    assertThat(purgedSse.isNew()).isTrue();
    assertThat(purgedSse.event().envelope().type()).isEqualTo("INCIDENT_PURGED");
    assertThat(sseReplayEventStore.isIncidentPurged(INCIDENT_ID)).isTrue();

    // 5. 종료 상황판 증거는 오래된 개인정보나 새로고침/재시도 안내 없이 수렴해야 한다.
    BoardDTO board = assembleTerminalBoard(completed, purgedEvent, purgedSse);
    assertThat(board.boardResponseVersion()).isGreaterThanOrEqualTo(completed.version());
    assertThat(board.slotRow("incident_terminal", TERMINAL_SOURCE_RESPONSE_ID).payload())
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("terminalStatus", "PURGED")
        .containsEntry("closedStatus", "purged")
        .containsEntry("closedAt", "2026-04-28T01:45:00Z")
        .containsEntry("writeDisabledReason", "purged")
        .containsEntry("localPurgeState", "completed")
        .doesNotContainKeys(
            "missing_person",
            "missingPerson",
            "displayName",
            "photoObjectKey",
            "lastSeenLocationText",
            "packageReloadUrl",
            "streamResubscribeHint");
    assertThat(serializedSlots(board))
        .doesNotContain(
            "가상 실종자",
            "photo/missing-person/sc12.jpg",
            "lastSeenLocationText",
            "packageReloadUrl",
            "streamResubscribeHint");
  }

  private void seedIncident() {
    jdbcTemplate.update(
        """
        INSERT INTO "incident" (
          id, source_incident_id, title, status, opened_at, closed_at, closed_by_account_id,
          version, created_at, updated_at
        )
        VALUES (?, ?, '종로구 인왕산 실종 신고', 'OPEN', ?, NULL, NULL, 1, ?, ?)
        """,
        INCIDENT_ID,
        SOURCE_INCIDENT_ID,
        OPENED_AT,
        OPENED_AT,
        OPENED_AT);
  }

  private void seedMissingPerson() {
    jdbcTemplate.update(
        """
        INSERT INTO missing_person (
          incident_id, display_name, photo_object_key, appearance_text,
          last_seen_location_text, last_seen_at, imported_at
        )
        VALUES (?, '가상 실종자 001', 'photo/missing-person/sc12.jpg',
          '남색 점퍼, 회색 등산화', '인왕산 북측 산책로 입구', ?, ?)
        """,
        INCIDENT_ID,
        OffsetDateTime.parse("2026-04-28T08:30:00+09:00"),
        OPENED_AT);
  }

  private void seedCommanderAssignment() {
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
        )
        VALUES ('10000000-0000-4000-8000-000000000101', ?, ?, 'INCIDENT_COMMANDER',
          ?, NULL, ?, ?)
        """,
        INCIDENT_ID,
        UUID.fromString(COMMANDER_ACCOUNT_ID),
        OPENED_AT,
        OPENED_AT,
        OPENED_AT);
  }

  private void seedOfflinePackageWithPii() {
    // 최종 상황판 검증이 단순 부재가 아니라 민감정보 제거임을 증명하도록 개인정보가 담긴 오래된 package를 심는다.
    jdbcTemplate.update(
        """
        INSERT INTO offline_package_manifest (
          id, incident_id, manifest_version, operational_period_id, overall_search_area_id,
          overall_search_area_version, manifest_hash, manifest_format_version, manifest_payload,
          expires_at, created_at, updated_at
        )
        VALUES (?, ?, 1, ?, 'overall-area-precinct-current', 1,
          'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 1,
          ?::jsonb, ?, ?, ?)
        """,
        PACKAGE_MANIFEST_ID,
        INCIDENT_ID.toString(),
        OP1_ID,
        """
        {
          "missing_person": {
            "displayName": "가상 실종자 001",
            "photoObjectKey": "photo/missing-person/sc12.jpg",
            "lastSeenLocationText": "인왕산 북측 산책로 입구"
          },
          "packageItems": [{"itemKey": "missing-person:sc12"}]
        }
        """,
        SERVER_TS.plusHours(24),
        SERVER_TS,
        SERVER_TS);
    jdbcTemplate.update(
        """
        INSERT INTO offline_package_installation (
          id, offline_package_manifest_id, police_phone_id, last_reported_by_account_id, status,
          total_item_count, completed_item_count, failed_item_count, failed_item_keys,
          last_error_code, last_reported_at, version, created_at, updated_at
        )
        VALUES ('pkg-status-sc12-001', ?, 'dev-alpha-phone-01',
          '11111111-1111-1111-1111-111111110005', 'READY',
          7, 7, 0, NULL, NULL, ?, 3, ?, ?)
        """,
        PACKAGE_MANIFEST_ID,
        SERVER_TS,
        SERVER_TS,
        SERVER_TS);
  }

  private OutboxRow singleOutboxRow(String eventType) {
    List<OutboxRow> rows =
        jdbcTemplate.query(
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
              AND event_type = ?
            ORDER BY created_at ASC
            """,
            (rs, rowNum) ->
                new OutboxRow(
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
            INCIDENT_ID,
            eventType);
    assertThat(rows).hasSize(1);
    return rows.get(0);
  }

  private Map<String, Object> readPayload(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("event payload must be valid JSON", exception);
    }
  }

  private BoardDTO assembleTerminalBoard(
      IncidentDataPurgeRun completed, OutboxRow purgedEvent, ReplayAppend purgedSse) {
    // S1-3 종료 행과 package badge 행을 포함해 S3-2가 소비하는 상황판 형태로 조립한다.
    return new BoardAssembler()
        .assemble(
            new BoardAssemblyRequest(
                INCIDENT_ID.toString(),
                "board-sc12-terminal-purged-001",
                0L,
                SERVER_TS,
                null,
                List.of(OP1_ID),
                "overall-area-hash-precinct-current",
                concat(
                    List.of(incidentTerminalRow(completed, purgedEvent, purgedSse)),
                    new PackageBadgeBoardAssembler(offlinePackageInstallationQuery)
                        .sourceRowsByIncident(INCIDENT_ID.toString()))));
  }

  private BoardSourceRow incidentTerminalRow(
      IncidentDataPurgeRun completed, OutboxRow purgedEvent, ReplayAppend purgedSse) {
    // 이 행에는 금지 키를 일부러 넣는다. SC-12는 종료 상황판이 이를 제거해야 한다.
    return new BoardSourceRow(
        "incident_terminal",
        "S1-3",
        TERMINAL_SOURCE_RESPONSE_ID,
        TERMINAL_BOARD_ROW_ID,
        "PURGED",
        completed.version(),
        purgedSse.replaySequence(),
        purgedEvent.eventId().toString(),
        "hash-sc12-terminal-purged",
        Map.ofEntries(
            Map.entry("incidentId", INCIDENT_ID.toString()),
            Map.entry("terminalStatus", "PURGED"),
            Map.entry("closedStatus", "purged"),
            Map.entry("closedAt", "2026-04-28T01:45:00Z"),
            Map.entry("writeDisabledReason", "purged"),
            Map.entry("localPurgeState", "completed"),
            Map.entry("missing_person", Map.of("displayName", "가상 실종자 001")),
            Map.entry("missingPerson", Map.of("photoObjectKey", "photo/missing-person/sc12.jpg")),
            Map.entry("lastSeenLocationText", "인왕산 북측 산책로 입구"),
            Map.entry("packageReloadUrl", "/api/incidents/" + INCIDENT_ID + "/packages/reload"),
            Map.entry("streamResubscribeHint", "/api/incidents/" + INCIDENT_ID + "/events")));
  }

  private int count(String table, String whereClause, Object... args) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause, Integer.class, args);
    return count == null ? 0 : count;
  }

  private String singleString(String sql) {
    return jdbcTemplate.queryForObject(sql, String.class, INCIDENT_ID);
  }

  private List<String> activeAssignmentAccountIds() {
    return jdbcTemplate.queryForList(
        """
        SELECT account_id
        FROM incident_assignment
        WHERE incident_id = ?
          AND revoked_at IS NULL
        ORDER BY account_id ASC
        """,
        String.class,
        INCIDENT_ID);
  }

  private List<String> packageStatuses() {
    return jdbcTemplate.queryForList(
        """
        SELECT status
        FROM offline_package_installation
        WHERE offline_package_manifest_id = ?
        ORDER BY id ASC
        """,
        String.class,
        PACKAGE_MANIFEST_ID);
  }

  private static ExternalAssignment supportAssignment() {
    return new ExternalAssignment(
        SOURCE_INCIDENT_ID + ":support-cmd",
        "11111111-1111-1111-1111-111111110006",
        "FIELD_COMMANDER",
        OffsetDateTime.parse("2026-04-28T11:00:00+09:00"));
  }

  private static String payloadString(OutboxRow row, String key) {
    return String.valueOf(row.payload().get(key));
  }

  private static long payloadLong(OutboxRow row, String key) {
    Object value = row.payload().get(key);
    assertThat(value).isInstanceOf(Number.class);
    return ((Number) value).longValue();
  }

  private static void assertNoPii(Map<String, Object> payload) {
    assertThat(String.valueOf(payload))
        .doesNotContain(
            "missing_person",
            "missingPerson",
            "displayName",
            "photoObjectKey",
            "appearanceText",
            "lastSeenLocationText",
            "가상 실종자",
            "photo/missing-person/sc12.jpg");
  }

  private static String serializedSlots(BoardDTO board) {
    return String.valueOf(board.slots());
  }

  private static List<BoardSourceRow> concat(
      List<BoardSourceRow> first, List<BoardSourceRow> second) {
    List<BoardSourceRow> rows = new ArrayList<>(first);
    rows.addAll(second);
    return List.copyOf(rows);
  }

  private record OutboxRow(
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
  }

  @TestConfiguration
  static class Sc12FixtureConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      // API, outbox, SSE, 상황판 검증이 같은 시간을 비교하도록 닫기 시각을 고정한다.
      return Clock.fixed(CLOSED_AT, ZoneOffset.UTC);
    }

    @Bean
    SearchAreaQuery searchAreaQuery() {
      return new SearchAreaQueryMock();
    }

    @Bean
    PurgeHook localSyncPurgeHook() {
      // 이 하네스는 조율 흐름과 이벤트 증거를 검증하므로 hook 본문은 성공 stub으로 둔다.
      return successfulHook(PurgeHookName.LOCAL_SYNC);
    }

    @Bean
    PurgeHook pathPurgeHook() {
      return successfulHook(PurgeHookName.PATH);
    }

    @Bean
    PurgeHook markerPhotoPurgeHook() {
      return successfulHook(PurgeHookName.MARKER_PHOTO);
    }

    private static PurgeHook successfulHook(PurgeHookName name) {
      return new PurgeHook() {
        @Override
        public PurgeHookName name() {
          return name;
        }

        @Override
        public PurgeHookResult purge(com.surimap.retention.purge.PurgeHookRequest request) {
          return PurgeHookResult.succeeded(1, 0);
        }
      };
    }
  }
}
