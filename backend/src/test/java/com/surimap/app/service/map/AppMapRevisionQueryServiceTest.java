package com.surimap.app.service.map;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.app.controller.map.response.AppMapRevisionResponse;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AppMapRevisionQueryServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000006111");
  private static final UUID OP_ID = UUID.fromString("65000000-0000-4000-8000-000000006111");
  private static final UUID AREA_A_ID = UUID.fromString("cccccccc-0000-4000-8000-000000006111");
  private static final UUID AREA_B_ID = UUID.fromString("cccccccc-0000-4000-8000-000000006112");
  private static final UUID ASSIGNMENT_A_ID =
      UUID.fromString("71000000-0000-4000-8000-000000006111");
  private static final UUID ASSIGNMENT_B_ID =
      UUID.fromString("71000000-0000-4000-8000-000000006112");
  private static final UUID ACCOUNT_A_ID =
      UUID.fromString("62000000-0000-4000-8000-000000006111");
  private static final UUID ACCOUNT_B_ID =
      UUID.fromString("62000000-0000-4000-8000-000000006112");
  private static final UUID COMMANDER_ID =
      UUID.fromString("63000000-0000-4000-8000-000000006111");
  private static final Instant BASE_TS = Instant.parse("2026-11-26T00:00:00Z");

  @Autowired private AppMapRevisionQueryService appMapRevisionQueryService;

  @BeforeEach
  void seedOperationalPeriodWithTwoAssignedAreas() {
    deleteSeededRows();
    insertIncident();
    insertOperationalPeriod();
    insertSearchArea(AREA_A_ID);
    insertSearchArea(AREA_B_ID);
    insertSearchAreaAssignment(ASSIGNMENT_A_ID, AREA_A_ID, ACCOUNT_A_ID);
    insertSearchAreaAssignment(ASSIGNMENT_B_ID, AREA_B_ID, ACCOUNT_B_ID);
  }

  @Test
  @DisplayName("다른 계정의 구역 배정이 바뀌어도 요청한 계정의 initial_markers revision은 그대로다")
  void revisions_assignmentOfOtherAccountChanges_keepsInitialMarkersRevision() {
    // given: 계정 B의 initial_markers revision을 받아 둔다.
    String before = initialMarkersRevision(ACCOUNT_B_ID);

    // when: 계정 A의 구역 배정을 해제한다.
    cancelAssignment(ASSIGNMENT_A_ID);

    // then: 계정 B의 initial_markers revision은 바뀌지 않는다.
    assertThat(initialMarkersRevision(ACCOUNT_B_ID)).isEqualTo(before);
  }

  @Test
  @DisplayName("요청한 계정의 구역 배정이 바뀌면 그 계정의 initial_markers revision이 바뀐다")
  void revisions_assignmentOfRequestingAccountChanges_changesInitialMarkersRevision() {
    // given: 계정 B의 initial_markers revision을 받아 둔다.
    String before = initialMarkersRevision(ACCOUNT_B_ID);

    // when: 계정 B의 구역 배정을 해제한다.
    cancelAssignment(ASSIGNMENT_B_ID);

    // then: 계정 B의 initial_markers revision이 바뀐다.
    assertThat(initialMarkersRevision(ACCOUNT_B_ID)).isNotEqualTo(before);
  }

  private void deleteSeededRows() {
    jdbcTemplate.update(
        "DELETE FROM operational_period WHERE incident_id = ?::uuid", INCIDENT_ID.toString());
    jdbcTemplate.update("DELETE FROM incident WHERE id = ?::uuid", INCIDENT_ID.toString());
  }

  private void insertIncident() {
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?::uuid, gen_random_uuid(), 'Account map revision incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertOperationalPeriod() {
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_by_account_id,
          started_at, version, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, 1, 'ACTIVE', 'INITIAL_IMPORT', ?::uuid, ?, 1, ?, ?)
        """,
        OP_ID.toString(),
        INCIDENT_ID.toString(),
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertSearchArea(UUID areaId) {
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
          id, operational_period_id, parent_search_area_id, name, area_level, geometry, status,
          version, created_by_account_id, created_at, updated_at
        )
        VALUES (
          ?::uuid, ?::uuid, NULL, ?, 'TEAM',
          ST_GeomFromText('POLYGON((126.910000 35.160000,126.918000 35.160000,126.918000 35.166000,126.910000 35.166000,126.910000 35.160000))', 4326),
          'ACTIVE', 1, ?::uuid, ?, ?
        )
        """,
        areaId.toString(),
        OP_ID.toString(),
        "area " + areaId,
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertSearchAreaAssignment(UUID assignmentId, UUID areaId, UUID accountId) {
    jdbcTemplate.update(
        """
        INSERT INTO search_area_assignment (
          id, search_area_id, assigned_account_id, assigned_by_account_id, assigned_at, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, 'ACTIVE', ?, ?)
        """,
        assignmentId.toString(),
        areaId.toString(),
        accountId.toString(),
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private String initialMarkersRevision(UUID accountId) {
    AppMapRevisionResponse response =
        appMapRevisionQueryService.revisions(INCIDENT_ID, OP_ID, accountId);
    return response.sources().stream()
        .filter(source -> "initial_markers".equals(source.source()))
        .findFirst()
        .orElseThrow()
        .revision();
  }

  private void cancelAssignment(UUID assignmentId) {
    jdbcTemplate.update(
        """
        UPDATE search_area_assignment
        SET status = 'CANCELLED', revoked_at = ?, updated_at = ?
        WHERE id = ?::uuid
        """,
        Timestamp.from(BASE_TS.plusSeconds(60)),
        Timestamp.from(BASE_TS.plusSeconds(60)),
        assignmentId.toString());
  }
}
