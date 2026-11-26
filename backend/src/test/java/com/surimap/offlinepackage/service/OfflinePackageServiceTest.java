package com.surimap.offlinepackage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse.AssignedArea;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "tileserver.mode=fixture")
class OfflinePackageServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-4000-8000-000000006101");
  private static final UUID OP_ID = UUID.fromString("65000000-0000-4000-8000-000000006101");
  private static final UUID OVERALL_AREA_ID =
      UUID.fromString("bbbbbbbb-0000-4000-8000-000000006101");
  private static final UUID AREA_A_ID = UUID.fromString("cccccccc-0000-4000-8000-000000006101");
  private static final UUID AREA_B_ID = UUID.fromString("cccccccc-0000-4000-8000-000000006102");
  private static final UUID ACCOUNT_A_ID =
      UUID.fromString("62000000-0000-4000-8000-000000006101");
  private static final UUID ACCOUNT_B_ID =
      UUID.fromString("62000000-0000-4000-8000-000000006102");
  private static final UUID COMMANDER_ID =
      UUID.fromString("63000000-0000-4000-8000-000000006101");
  // A가 쓰던 업무폰이다. 서버에는 이 업무폰의 기록이 없다.
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-4000-8000-000000006101");
  private static final Instant BASE_TS = Instant.parse("2026-11-26T00:00:00Z");

  @Autowired private OfflinePackageService offlinePackageService;

  @BeforeEach
  void seedIncidentWithTwoAssignedAccounts() {
    deleteSeededRows();
    insertAccount(ACCOUNT_A_ID, "TEAM");
    insertAccount(ACCOUNT_B_ID, "TEAM");
    insertAccount(COMMANDER_ID, "COMMAND");
    insertIncident();
    insertIncidentAssignment("61000000-0000-4000-8000-000000006101", ACCOUNT_A_ID);
    insertIncidentAssignment("61000000-0000-4000-8000-000000006102", ACCOUNT_B_ID);
    insertOperationalPeriod();
    insertSearchArea(OVERALL_AREA_ID, null, "OVERALL");
    insertSearchArea(AREA_A_ID, OVERALL_AREA_ID, "TEAM");
    insertSearchArea(AREA_B_ID, OVERALL_AREA_ID, "TEAM");
    insertSearchAreaAssignment("71000000-0000-4000-8000-000000006101", AREA_A_ID, ACCOUNT_A_ID);
    insertSearchAreaAssignment("71000000-0000-4000-8000-000000006102", AREA_B_ID, ACCOUNT_B_ID);
  }

  @Test
  @DisplayName("A가 쓰던 업무폰에서 계정 B로 manifest를 받으면 B의 배정 구역만 담긴다")
  void manifest_accountBOnPhoneUsedByA_containsOnlyAreaOfB() {
    // when: A가 쓰던 업무폰에 로그인한 계정 B가 그 업무폰 ID로 manifest를 요청한다.
    OfflinePackageManifestResponse manifest =
        offlinePackageService.manifestWithIncidentAccess(
            INCIDENT_ID.toString(), POLICE_PHONE_ID.toString(), appAuthentication(ACCOUNT_B_ID));

    // then: 배정 구역에는 B에게 배정된 구역만 있다.
    assertThat(manifest.assignedAreas())
        .extracting(AssignedArea::areaId)
        .containsExactly(AREA_B_ID.toString());
  }

  @Test
  @DisplayName("업무폰 ID 없이 manifest를 요청해도 요청한 계정의 배정 구역만 담긴다")
  void manifest_withoutPolicePhoneId_containsOnlyAreaOfRequestingAccount() {
    // when: 계정 B가 업무폰 ID 없이 manifest를 요청한다.
    OfflinePackageManifestResponse manifest =
        offlinePackageService.manifestWithIncidentAccess(
            INCIDENT_ID.toString(), null, appAuthentication(ACCOUNT_B_ID));

    // then: 배정 구역에는 B에게 배정된 구역만 있다.
    assertThat(manifest.assignedAreas())
        .extracting(AssignedArea::areaId)
        .containsExactly(AREA_B_ID.toString());
  }

  @Test
  @DisplayName("A가 같은 업무폰 ID로 먼저 manifest를 받은 뒤 B가 받으면 B의 배정 구역만 담긴다")
  void manifest_accountBAfterAccountAOnSamePhone_containsOnlyAreaOfB() {
    // given: A가 그 업무폰 ID로 먼저 manifest를 받아서 사건의 manifest 행이 생겼다.
    offlinePackageService.manifestWithIncidentAccess(
        INCIDENT_ID.toString(), POLICE_PHONE_ID.toString(), appAuthentication(ACCOUNT_A_ID));

    // when: 같은 업무폰에 로그인한 B가 같은 업무폰 ID로 manifest를 요청한다.
    OfflinePackageManifestResponse manifest =
        offlinePackageService.manifestWithIncidentAccess(
            INCIDENT_ID.toString(), POLICE_PHONE_ID.toString(), appAuthentication(ACCOUNT_B_ID));

    // then: 배정 구역에는 B에게 배정된 구역만 있다.
    assertThat(manifest.assignedAreas())
        .extracting(AssignedArea::areaId)
        .containsExactly(AREA_B_ID.toString());
  }

  @Test
  @DisplayName("manifest 행이 있는데 원천 조회가 비면 B의 manifest에 다른 구역이 담기지 않는다")
  void manifest_sourceEmptyAfterManifestCreated_containsNoAssignedArea() {
    // given: A가 먼저 manifest를 받은 뒤 전체 수색 구역이 취소되어 원천 조회가 빈다.
    offlinePackageService.manifestWithIncidentAccess(
        INCIDENT_ID.toString(), POLICE_PHONE_ID.toString(), appAuthentication(ACCOUNT_A_ID));
    jdbcTemplate.update(
        "UPDATE search_area SET status = 'CANCELLED' WHERE id = ?::uuid",
        OVERALL_AREA_ID.toString());

    // when: 같은 업무폰에 로그인한 B가 같은 업무폰 ID로 manifest를 요청한다.
    OfflinePackageManifestResponse manifest =
        offlinePackageService.manifestWithIncidentAccess(
            INCIDENT_ID.toString(), POLICE_PHONE_ID.toString(), appAuthentication(ACCOUNT_B_ID));

    // then: 배정 구역은 비어 있다.
    assertThat(manifest.assignedAreas()).isEmpty();
  }

  private void deleteSeededRows() {
    jdbcTemplate.update(
        """
        DELETE FROM offline_package_installation
        WHERE offline_package_manifest_id IN (
          SELECT id FROM offline_package_manifest WHERE incident_id = ?::uuid
        )
        """,
        INCIDENT_ID.toString());
    jdbcTemplate.update(
        "DELETE FROM offline_package_manifest WHERE incident_id = ?::uuid", INCIDENT_ID.toString());
    jdbcTemplate.update(
        "DELETE FROM operational_period WHERE incident_id = ?::uuid", INCIDENT_ID.toString());
    jdbcTemplate.update(
        "DELETE FROM incident_assignment WHERE incident_id = ?::uuid", INCIDENT_ID.toString());
    jdbcTemplate.update("DELETE FROM incident WHERE id = ?::uuid", INCIDENT_ID.toString());
    jdbcTemplate.update(
        "DELETE FROM account WHERE id IN (?::uuid, ?::uuid, ?::uuid)",
        ACCOUNT_A_ID.toString(),
        ACCOUNT_B_ID.toString(),
        COMMANDER_ID.toString());
  }

  private void insertAccount(UUID accountId, String accountType) {
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?, '{noop}fixture', ?, ?, 'MISSING_TEAM', 'ACTIVE', ?, ?)
        """,
        accountId.toString(),
        "acct-" + accountId,
        "account " + accountId,
        accountType,
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertIncident() {
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?::uuid, gen_random_uuid(), 'Account manifest incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertIncidentAssignment(String assignmentId, UUID accountId) {
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, 'MEMBER', ?, ?, ?)
        """,
        assignmentId,
        INCIDENT_ID.toString(),
        accountId.toString(),
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

  private void insertSearchArea(UUID areaId, UUID parentAreaId, String areaLevel) {
    jdbcTemplate.update(
        """
        INSERT INTO search_area (
          id, operational_period_id, parent_search_area_id, name, area_level, geometry, status,
          version, created_by_account_id, created_at, updated_at
        )
        VALUES (
          ?::uuid, ?::uuid, ?::uuid, ?, ?,
          ST_GeomFromText('POLYGON((126.910000 35.160000,126.918000 35.160000,126.918000 35.166000,126.910000 35.166000,126.910000 35.160000))', 4326),
          'ACTIVE', 1, ?::uuid, ?, ?
        )
        """,
        areaId.toString(),
        OP_ID.toString(),
        parentAreaId == null ? null : parentAreaId.toString(),
        "area " + areaId,
        areaLevel,
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private void insertSearchAreaAssignment(String assignmentId, UUID areaId, UUID accountId) {
    jdbcTemplate.update(
        """
        INSERT INTO search_area_assignment (
          id, search_area_id, assigned_account_id, assigned_by_account_id, assigned_at, status,
          created_at, updated_at
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, 'ACTIVE', ?, ?)
        """,
        assignmentId,
        areaId.toString(),
        accountId.toString(),
        COMMANDER_ID.toString(),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS),
        Timestamp.from(BASE_TS));
  }

  private static SuriMapAuthentication appAuthentication(UUID accountId) {
    return new SuriMapAuthentication(
        accountId.toString(),
        AccountType.TEAM,
        OrganizationType.MISSING_TEAM,
        Channel.APP,
        POLICE_PHONE_ID.toString(),
        List.of(new SimpleGrantedAuthority("MEMBER")));
  }
}
