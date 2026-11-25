package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.incident.service.IncidentAssignmentView;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IncidentAssignmentViewTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000005711");
  private static final UUID ACTIVE_ACCOUNT_ID =
      UUID.fromString("62000000-0000-0000-0000-000000005711");
  private static final UUID REVOKED_ACCOUNT_ID =
      UUID.fromString("62000000-0000-0000-0000-000000005712");
  private static final Instant BASE_TS = Instant.parse("2026-11-25T00:00:00Z");

  @Autowired private IncidentAssignmentView incidentAssignmentView;

  @BeforeEach
  void seedIncident() {
    jdbcTemplate.execute("TRUNCATE TABLE incident_assignment, account, incident CASCADE");
    Timestamp base = Timestamp.from(BASE_TS);
    Timestamp revokedAt = Timestamp.from(BASE_TS.plusSeconds(3600));
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, gen_random_uuid(), 'Assignment view incident', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        base,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO account (
          id, login_id, password_hash, display_name, account_type, organization_type, status,
          created_at, updated_at
        )
        VALUES
          (?, 'acct-5711-active', '{noop}fixture', '배정 중 계정', 'TEAM', 'MISSING_TEAM',
           'ACTIVE', ?, ?),
          (?, 'acct-5711-revoked', '{noop}fixture', '배정 해제 계정', 'TEAM', 'MISSING_TEAM',
           'ACTIVE', ?, ?)
        """,
        ACTIVE_ACCOUNT_ID,
        base,
        base,
        REVOKED_ACCOUNT_ID,
        base,
        base);
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (
          id, incident_id, account_id, incident_role, assigned_at, revoked_at,
          created_at, updated_at
        )
        VALUES
          (gen_random_uuid(), ?, ?, 'MEMBER', ?, NULL, ?, ?),
          (gen_random_uuid(), ?, ?, 'MEMBER', ?, ?, ?, ?)
        """,
        INCIDENT_ID,
        ACTIVE_ACCOUNT_ID,
        base,
        base,
        base,
        INCIDENT_ID,
        REVOKED_ACCOUNT_ID,
        base,
        revokedAt,
        base,
        revokedAt);
  }

  @Test
  @DisplayName("사건에 배정됐던 계정의 이름은 배정이 해제된 뒤에도 찾을 수 있다")
  void accountDisplayNames_revokedAssignment_includesRevokedAccountName() {
    assertThat(incidentAssignmentView.accountDisplayNames(INCIDENT_ID))
        .containsEntry(ACTIVE_ACCOUNT_ID, "배정 중 계정")
        .containsEntry(REVOKED_ACCOUNT_ID, "배정 해제 계정");
  }
}
