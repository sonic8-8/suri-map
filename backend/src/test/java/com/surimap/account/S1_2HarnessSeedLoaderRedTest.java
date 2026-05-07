package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L2-T01 S1-2 account/police_phone seed loader RED")
class S1_2HarnessSeedLoaderRedTest {

  private static final List<String> REQUIRED_ACCOUNT_IDS =
      List.of(
          "acct-precinct-cmd",
          "acct-precinct-car",
          "acct-precinct-team",
          "acct-cmd-alpha",
          "acct-team-alpha",
          "acct-support-cmd",
          "acct-support-car",
          "acct-support-team");

  private static final List<String> REQUIRED_POLICE_PHONE_IDS =
      List.of(
          "dev-precinct-cmd-phone-01",
          "dev-precinct-car-01",
          "dev-precinct-phone-01",
          "dev-alpha-cmd-phone-01",
          "dev-alpha-phone-01",
          "dev-support-cmd-phone-01",
          "dev-support-car-01",
          "dev-support-phone-01");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("seed loader materializes canonical harness account rows")
  void seed_loader_materializes_canonical_harness_account_rows() {
    var rows =
        jdbcTemplate.queryForList(
            """
            SELECT login_id, account_type, organization_type, status
            FROM account
            WHERE login_id IN (?, ?, ?, ?, ?, ?, ?, ?)
            ORDER BY login_id
            """,
            REQUIRED_ACCOUNT_IDS.toArray());

    assertThat(rows).hasSize(REQUIRED_ACCOUNT_IDS.size());
    assertThat(rows)
        .extracting(
            row -> row.get("login_id"),
            row -> row.get("account_type"),
            row -> row.get("organization_type"),
            row -> row.get("status"))
        .containsExactlyInAnyOrder(
            tuple("acct-precinct-cmd", "COMMAND", "POLICE_SUBSTATION", "ACTIVE"),
            tuple("acct-precinct-car", "PATROL_CAR", "POLICE_SUBSTATION", "ACTIVE"),
            tuple("acct-precinct-team", "TEAM", "POLICE_SUBSTATION", "ACTIVE"),
            tuple("acct-cmd-alpha", "COMMAND", "MISSING_TEAM", "ACTIVE"),
            tuple("acct-team-alpha", "TEAM", "MISSING_TEAM", "ACTIVE"),
            tuple("acct-support-cmd", "COMMAND", "SUPPORT_UNIT", "ACTIVE"),
            tuple("acct-support-car", "PATROL_CAR", "SUPPORT_UNIT", "ACTIVE"),
            tuple("acct-support-team", "TEAM", "SUPPORT_UNIT", "ACTIVE"));
  }

  @Test
  @DisplayName("seed loader materializes canonical harness police_phone rows without phone_type")
  void seed_loader_materializes_canonical_harness_police_phone_rows_without_phone_type() {
    var columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_name = 'police_phone'
            """,
            String.class);

    assertThat(columns).contains("id", "phone_code", "status");
    assertThat(columns).doesNotContain("phone_type");

    var rows =
        jdbcTemplate.queryForList(
            """
            SELECT phone_code, status
            FROM police_phone
            WHERE phone_code IN (?, ?, ?, ?, ?, ?, ?, ?)
            ORDER BY phone_code
            """,
            REQUIRED_POLICE_PHONE_IDS.toArray());

    assertThat(rows).hasSize(REQUIRED_POLICE_PHONE_IDS.size());
    assertThat(rows)
        .extracting(row -> row.get("phone_code"))
        .containsExactlyInAnyOrderElementsOf(REQUIRED_POLICE_PHONE_IDS);
    assertThat(rows).allSatisfy(row -> assertThat(row.get("status")).isEqualTo("ACTIVE"));
  }
}
