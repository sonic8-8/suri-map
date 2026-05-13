package com.surimap.policephone;

import com.surimap.account.AccountIdentityCatalog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PolicePhoneDbFixtureSupport {

  private static final UUID UNREGISTERED_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID UNASSIGNED_ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110009");
  private static final UUID ASSIGNMENT_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
  private static final Instant ASSIGNED_AT = Instant.parse("2026-05-08T00:00:00Z");

  private PolicePhoneDbFixtureSupport() {}

  public static void ensureGuardFixtures(JdbcTemplate jdbcTemplate) {
    ensureAccount(
        jdbcTemplate,
        AccountIdentityCatalog.PRECINCT_TEAM_ID,
        AccountIdentityCatalog.PRECINCT_TEAM_CODE,
        "종로 지구대 팀",
        "TEAM",
        "POLICE_SUBSTATION");
    ensureAccount(
        jdbcTemplate,
        UNASSIGNED_ACCOUNT_ID,
        "acct-unassigned-phone",
        "미배정 폴리폰 계정",
        "TEAM",
        "POLICE_SUBSTATION");

    ensurePolicePhone(
        jdbcTemplate,
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        "dev-precinct-phone-01",
        "종로 지구대 팀 폴리폰",
        AccountIdentityCatalog.PRECINCT_TEAM_ID,
        true);
    ensurePolicePhone(
        jdbcTemplate,
        UNREGISTERED_POLICE_PHONE_ID,
        "dev-precinct-cmd-phone-01",
        "종로 지구대 지휘 폴리폰",
        AccountIdentityCatalog.PRECINCT_TEAM_ID,
        false);
    ensurePolicePhone(
        jdbcTemplate,
        PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID,
        "dev-unassigned-phone-01",
        "미배정 폴리폰",
        UNASSIGNED_ACCOUNT_ID,
        true);
    ensureAssignedFixture(jdbcTemplate);
  }

  private static void ensureAccount(
      JdbcTemplate jdbcTemplate,
      UUID id,
      String loginId,
      String displayName,
      String accountType,
      String organizationType) {
    jdbcTemplate.update(
        """
        MERGE INTO account (
            id, login_id, password_hash, display_name, account_type, organization_type, status
        ) KEY(id) VALUES (?, ?, '{noop}fixture', ?, ?, ?, 'ACTIVE')
        """,
        id,
        loginId,
        displayName,
        accountType,
        organizationType);
  }

  private static void ensurePolicePhone(
      JdbcTemplate jdbcTemplate,
      UUID id,
      String phoneCode,
      String displayName,
      UUID accountId,
      boolean registered) {
    jdbcTemplate.update(
        """
        MERGE INTO police_phone (
            id,
            phone_code,
            display_name,
            account_id,
            status,
            registered,
            last_heartbeat_at,
            last_sync_at,
            heartbeat_sequence,
            last_heartbeat_event_id,
            version
        ) KEY(id) VALUES (?, ?, ?, ?, 'ACTIVE', ?, NULL, NULL, 0, NULL, 1)
        """,
        id,
        phoneCode,
        displayName,
        accountId,
        registered);
  }

  private static void ensureAssignedFixture(JdbcTemplate jdbcTemplate) {
    jdbcTemplate.update(
        """
        MERGE INTO incident_assignment (
            id, incident_id, account_id, incident_role, assigned_at, revoked_at, created_at, updated_at
        ) KEY(id) VALUES (?, ?, ?, 'MEMBER', ?, NULL, ?, ?)
        """,
        ASSIGNMENT_ID,
        PolicePhoneFixtures.INCIDENT_ID,
        AccountIdentityCatalog.PRECINCT_TEAM_ID,
        ASSIGNED_AT,
        ASSIGNED_AT,
        ASSIGNED_AT);
  }
}
