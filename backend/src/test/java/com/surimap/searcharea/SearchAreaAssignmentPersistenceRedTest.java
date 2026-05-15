package com.surimap.searcharea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.api.controller.searcharea.request.AssignSearchAreaRequest;
import com.surimap.api.controller.searcharea.response.SearchAreaAssignmentResponse;
import com.surimap.api.service.searcharea.SearchAreaApiException;
import com.surimap.api.service.searcharea.SearchAreaApiService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("SearchArea assignment MyBatis persistence")
@Tag("integration")
class SearchAreaAssignmentPersistenceRedTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000002486");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000002486");
  private static final UUID NEXT_OP_ID = UUID.fromString("70000000-0000-0000-0000-000000002487");
  private static final UUID AREA_ID = UUID.fromString("20000000-0000-0000-0000-000000002486");
  private static final UUID COMMANDER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111112486");
  private static final UUID AUTH_ACCOUNT_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222486");
  private static final UUID ASSIGNEE_ONE =
      UUID.fromString("33333333-3333-3333-3333-333333332486");
  private static final UUID ASSIGNEE_TWO =
      UUID.fromString("44444444-4444-4444-4444-444444442486");
  private static final UUID ASSIGNEE_THREE =
      UUID.fromString("55555555-5555-5555-5555-555555552486");
  private static final OffsetDateTime CLIENT_TS =
      OffsetDateTime.parse("2026-05-12T09:30:00+09:00");

  @Autowired private SearchAreaApiService service;
  @Autowired private SearchAreaAssignmentQuery assignmentQuery;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("migration creates UUID search_area_assignment with active partial uniqueness")
  void migration_creates_search_area_assignment_contract() {
    List<Map<String, Object>> columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_name = 'search_area_assignment'
            ORDER BY ordinal_position
            """);

    assertThat(columns)
        .extracting(row -> row.get("column_name"))
        .containsExactly(
            "id",
            "search_area_id",
            "assigned_account_id",
            "assigned_by_account_id",
            "assigned_at",
            "revoked_at",
            "status",
            "memo",
            "created_at",
            "updated_at");
    assertThat(columns)
        .filteredOn(row -> row.get("column_name").equals("id"))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("data_type")).isEqualTo("uuid");
              assertThat(row.get("is_nullable")).isEqualTo("NO");
            });

    String partialUniqueIndex =
        jdbcTemplate.queryForObject(
            """
            SELECT indexdef
            FROM pg_indexes
            WHERE tablename = 'search_area_assignment'
              AND indexname = 'ux_search_area_assignment_active'
            """,
            String.class);
    assertThat(partialUniqueIndex)
        .contains("UNIQUE")
        .contains("search_area_id")
        .contains("assigned_account_id")
        .contains("revoked_at IS NULL");
  }

  @Test
  @DisplayName("assign persists ACTIVE rows with authenticated assigner and exposes active DB query")
  void assign_persists_active_rows_with_authenticated_assigner_and_by_op_query() {
    seedOpenIncidentWithActiveOperationalPeriod();
    seedSearchArea(AREA_ID, 4L);
    authenticateAs(AUTH_ACCOUNT_ID);

    SearchAreaAssignmentResponse response =
        service.assign(
            AREA_ID,
            new AssignSearchAreaRequest(
                INCIDENT_ID,
                OP_ID,
                List.of(ASSIGNEE_ONE, ASSIGNEE_TWO),
                "1팀 담당",
                CLIENT_TS),
            "idem-search-area-assignment-persistence-248");

    assertThat(response.searchAreaId()).isEqualTo(AREA_ID);
    assertThat(response.opId()).isEqualTo(OP_ID);
    assertThat(response.assignmentIds()).hasSize(2);
    assertThat(response.version()).isEqualTo(5L);

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT id, assigned_account_id, assigned_by_account_id, assigned_at, status, memo
            FROM search_area_assignment
            WHERE search_area_id = ?::uuid
            ORDER BY assigned_account_id
            """,
            AREA_ID.toString());

    assertThat(rows).hasSize(2);
    assertThat(rows).extracting(row -> row.get("id")).containsExactlyInAnyOrderElementsOf(response.assignmentIds());
    assertThat(rows).extracting(row -> row.get("assigned_account_id")).containsExactly(ASSIGNEE_ONE, ASSIGNEE_TWO);
    assertThat(rows).allSatisfy(
        row -> {
          assertThat(row.get("assigned_by_account_id")).isEqualTo(AUTH_ACCOUNT_ID);
          assertThat(row.get("assigned_at")).isEqualTo(Timestamp.from(CLIENT_TS.toInstant()));
          assertThat(row.get("status")).isEqualTo("ACTIVE");
          assertThat(row.get("memo")).isEqualTo("1팀 담당");
        });

    List<SearchAreaAssignmentRow> activeRows = assignmentQuery.byOp(OP_ID);
    assertThat(activeRows).hasSize(2);
    assertThat(activeRows).extracting(SearchAreaAssignmentRow::id).containsExactlyInAnyOrderElementsOf(response.assignmentIds());
    assertThat(activeRows).allSatisfy(
        row -> {
          assertThat(row.searchAreaId()).isEqualTo(AREA_ID);
          assertThat(row.assignedByAccountId()).isEqualTo(AUTH_ACCOUNT_ID);
          assertThat(row.assignedAt()).isEqualTo(CLIENT_TS.toInstant());
          assertThat(row.revokedAt()).isNull();
          assertThat(row.status()).isEqualTo("ACTIVE");
          assertThat(row.version()).isEqualTo(5L);
        });
  }

  @Test
  @DisplayName("assign falls back to active OP started_by_account_id when auth UUID is unavailable")
  void assign_uses_active_op_starter_when_auth_uuid_is_absent() {
    seedOpenIncidentWithActiveOperationalPeriod();
    seedSearchArea(AREA_ID, 1L);
    SecurityContextHolder.clearContext();

    service.assign(
        AREA_ID,
        new AssignSearchAreaRequest(
            INCIDENT_ID, OP_ID, List.of(ASSIGNEE_ONE), "fallback", CLIENT_TS),
        "idem-search-area-assignment-fallback-248");

    UUID assignedBy =
        jdbcTemplate.queryForObject(
            """
            SELECT assigned_by_account_id
            FROM search_area_assignment
            WHERE search_area_id = ?::uuid
            """,
            UUID.class,
            AREA_ID.toString());

    assertThat(assignedBy).isEqualTo(COMMANDER_ID);
  }

  @Test
  @DisplayName("reassign revokes previous active rows, inserts new rows, and increments area once")
  void reassign_revokes_previous_active_rows_and_exposes_area_history_query() {
    seedOpenIncidentWithActiveOperationalPeriod();
    seedSearchArea(AREA_ID, 7L);
    authenticateAs(AUTH_ACCOUNT_ID);

    SearchAreaAssignmentResponse first =
        service.assign(
            AREA_ID,
            new AssignSearchAreaRequest(
                INCIDENT_ID, OP_ID, List.of(ASSIGNEE_ONE, ASSIGNEE_TWO), "first", CLIENT_TS),
            "idem-search-area-assignment-first-248");
    SearchAreaAssignmentResponse second =
        service.assign(
            AREA_ID,
            new AssignSearchAreaRequest(
                INCIDENT_ID,
                OP_ID,
                List.of(ASSIGNEE_ONE, ASSIGNEE_THREE),
                "replace",
                CLIENT_TS.plusMinutes(5)),
            "idem-search-area-assignment-second-248");

    assertThat(first.version()).isEqualTo(8L);
    assertThat(second.version()).isEqualTo(9L);
    Long areaVersion =
        jdbcTemplate.queryForObject(
            "SELECT version FROM search_area WHERE id = ?::uuid", Long.class, AREA_ID.toString());
    assertThat(areaVersion).isEqualTo(9L);

    List<Map<String, Object>> history =
        jdbcTemplate.queryForList(
            """
            SELECT assigned_account_id, status, revoked_at
            FROM search_area_assignment
            WHERE search_area_id = ?::uuid
            ORDER BY created_at, assigned_account_id
            """,
            AREA_ID.toString());

    assertThat(history).hasSize(4);
    assertThat(history.subList(0, 2))
        .allSatisfy(
            row -> {
              assertThat(row.get("status")).isEqualTo("CANCELLED");
              assertThat(row.get("revoked_at")).isNotNull();
            });
    assertThat(history.subList(2, 4))
        .allSatisfy(
            row -> {
              assertThat(row.get("status")).isEqualTo("ACTIVE");
              assertThat(row.get("revoked_at")).isNull();
            });

    List<SearchAreaAssignmentRow> byOp = assignmentQuery.byOp(OP_ID);
    assertThat(byOp)
        .extracting(SearchAreaAssignmentRow::assignedAccountId)
        .containsExactlyInAnyOrder(ASSIGNEE_ONE, ASSIGNEE_THREE);
    assertThat(byOp).allSatisfy(row -> assertThat(row.status()).isEqualTo("ACTIVE"));

    List<SearchAreaAssignmentRow> byArea = assignmentQuery.byArea(AREA_ID);
    assertThat(byArea).hasSize(4);
    assertThat(byArea).extracting(SearchAreaAssignmentRow::status).contains("CANCELLED", "ACTIVE");
  }

  @Test
  @DisplayName("assign rejects a search_area that no longer belongs to the current OP")
  void assign_rejects_non_current_op_area() {
    seedOpenIncidentWithActiveOperationalPeriod();
    seedSearchArea(AREA_ID, 1L);
    replaceActiveOperationalPeriod(NEXT_OP_ID);

    assertThatThrownBy(
            () ->
                service.assign(
                    AREA_ID,
                    new AssignSearchAreaRequest(
                        INCIDENT_ID, OP_ID, List.of(ASSIGNEE_ONE), "stale", CLIENT_TS),
                    "idem-search-area-assignment-stale-op-248"))
        .isInstanceOfSatisfying(
            SearchAreaApiException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo("write_conflict"));

    Integer assignmentCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM search_area_assignment WHERE search_area_id = ?::uuid",
            Integer.class,
            AREA_ID.toString());
    assertThat(assignmentCount).isZero();
  }

  @Test
  @DisplayName("assign rejects duplicate assignees before DB unique constraint violation")
  void assign_rejects_duplicate_assignees() {
    seedOpenIncidentWithActiveOperationalPeriod();
    seedSearchArea(AREA_ID, 1L);

    assertThatThrownBy(
            () ->
                service.assign(
                    AREA_ID,
                    new AssignSearchAreaRequest(
                        INCIDENT_ID,
                        OP_ID,
                        List.of(ASSIGNEE_ONE, ASSIGNEE_ONE),
                        "duplicate",
                        CLIENT_TS),
                    "idem-search-area-assignment-duplicate-248"))
        .isInstanceOfSatisfying(
            SearchAreaApiException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo("write_conflict"));

    Integer assignmentCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM search_area_assignment WHERE search_area_id = ?::uuid",
            Integer.class,
            AREA_ID.toString());
    assertThat(assignmentCount).isZero();
  }

  private void seedOpenIncidentWithActiveOperationalPeriod() {
    Instant now = Instant.parse("2026-05-12T00:00:00Z");
    Timestamp nowTimestamp = Timestamp.from(now);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
            id, source_incident_id, title, status, opened_at,
            closed_at, closed_by_account_id, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?, 'OPEN', ?, NULL, NULL, 1, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET status = 'OPEN',
            updated_at = EXCLUDED.updated_at
        """,
        INCIDENT_ID.toString(),
        INCIDENT_ID.toString(),
        "S2 SearchArea assignment persistence red",
        nowTimestamp,
        nowTimestamp,
        nowTimestamp);

    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, reason_memo,
            started_by_account_id, ended_by_account_id, started_at, ended_at,
            version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 1, 'ACTIVE', 'INITIAL', NULL,
                ?::uuid, NULL, ?, NULL, 1, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET status = 'ACTIVE',
            updated_at = EXCLUDED.updated_at
        """,
        OP_ID.toString(),
        INCIDENT_ID.toString(),
        COMMANDER_ID.toString(),
        nowTimestamp,
        nowTimestamp,
        nowTimestamp);
  }

  private void seedSearchArea(UUID id, long version) {
    Instant now = Instant.parse("2026-05-12T00:10:00Z");
    Timestamp nowTimestamp = Timestamp.from(now);
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
            id, operational_period_id, parent_search_area_id, name, area_level,
            geometry, status, version, created_by_account_id, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, NULL, 'UNIT-1', 'UNIT',
                ST_GeomFromText(?, 4326), 'ACTIVE', ?, ?::uuid, ?, ?)
        """,
        id.toString(),
        OP_ID.toString(),
        "POLYGON((126.9101 35.1601,126.9111 35.1601,126.9111 35.1611,126.9101 35.1611,126.9101 35.1601))",
        version,
        COMMANDER_ID.toString(),
        nowTimestamp,
        nowTimestamp);
  }

  private void replaceActiveOperationalPeriod(UUID nextOpId) {
    Instant now = Instant.parse("2026-05-12T01:00:00Z");
    Timestamp nowTimestamp = Timestamp.from(now);
    jdbcTemplate.update(
        """
        UPDATE operational_period
        SET status = 'ENDED',
            ended_by_account_id = ?::uuid,
            ended_at = ?,
            updated_at = ?
        WHERE id = ?::uuid
        """,
        COMMANDER_ID.toString(),
        nowTimestamp,
        nowTimestamp,
        OP_ID.toString());
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
            id, incident_id, sequence_number, status, reason, reason_memo,
            started_by_account_id, ended_by_account_id, started_at, ended_at,
            version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 2, 'ACTIVE', 'MANUAL_RESTART', NULL,
                ?::uuid, NULL, ?, NULL, 1, ?, ?)
        """,
        nextOpId.toString(),
        INCIDENT_ID.toString(),
        COMMANDER_ID.toString(),
        nowTimestamp,
        nowTimestamp,
        nowTimestamp);
  }

  private static void authenticateAs(UUID accountId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new SuriMapAuthentication(
                accountId.toString(),
                AccountType.COMMAND,
                OrganizationType.POLICE_SUBSTATION,
                Channel.WEB,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_COMMANDER"))));
  }
}
