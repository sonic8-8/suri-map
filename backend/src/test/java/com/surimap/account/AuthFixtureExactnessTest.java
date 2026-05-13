package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.account.fixture.AccountPolicePhoneFixtures;
import com.surimap.account.fixture.AccountPolicePhoneSeedLoader;
import com.surimap.account.fixture.RoleChannelMatrixFixtures;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthFixtureExactnessTest {

  @Test
  @DisplayName("account and policePhone fixture IDs stay UUID while harness codes stay aliases")
  void account_and_policePhone_fixture_ids_stay_uuid_while_harness_codes_stay_aliases() {
    assertThat(AccountPolicePhoneFixtures.INCIDENT_ALIAS).isEqualTo("inc-precinct-first-001");
    assertThat(AccountPolicePhoneFixtures.OP1_ALIAS).isEqualTo("op-precinct-001-op1");

    assertThat(AccountPolicePhoneFixtures.accountIds())
        .containsExactlyInAnyOrder(
            AccountIdentityCatalog.PRECINCT_COMMANDER_ID,
            AccountIdentityCatalog.PRECINCT_PATROL_ID,
            AccountIdentityCatalog.PRECINCT_TEAM_ID,
            AccountIdentityCatalog.ALPHA_COMMANDER_ID,
            AccountIdentityCatalog.ALPHA_TEAM_ID,
            AccountIdentityCatalog.SUPPORT_COMMANDER_ID,
            AccountIdentityCatalog.SUPPORT_PATROL_ID,
            AccountIdentityCatalog.SUPPORT_TEAM_ID);

    assertThat(AccountPolicePhoneFixtures.policePhoneIds())
        .allSatisfy(id -> assertThat(id.toString()).matches("[0-9a-f-]{36}"));

    assertThat(AccountPolicePhoneFixtures.accountCodes())
        .containsExactlyInAnyOrder(
            "acct-precinct-cmd",
            "acct-precinct-car",
            "acct-precinct-team",
            "acct-cmd-alpha",
            "acct-team-alpha",
            "acct-support-cmd",
            "acct-support-car",
            "acct-support-team");

    assertThat(AccountPolicePhoneFixtures.policePhoneCodes())
        .containsExactlyInAnyOrder(
            "dev-precinct-cmd-phone-01",
            "dev-precinct-car-01",
            "dev-precinct-phone-01",
            "dev-alpha-cmd-phone-01",
            "dev-alpha-phone-01",
            "dev-support-cmd-phone-01",
            "dev-support-car-01",
            "dev-support-phone-01");
  }

  @Test
  @DisplayName("COMMANDER TEAM PATROL are fixture aliases mapped to real account types and roles")
  void fixture_aliases_map_to_real_account_types_and_roles() {
    assertThat(AccountPolicePhoneFixtures.precinctCommander().accountType())
        .isEqualTo(AccountType.COMMAND);
    assertThat(AccountPolicePhoneFixtures.precinctCommander().organizationType())
        .isEqualTo(OrganizationType.POLICE_SUBSTATION);
    assertThat(AccountPolicePhoneFixtures.precinctCommander().roles())
        .containsExactly(Role.FIELD_COMMANDER);

    assertThat(AccountPolicePhoneFixtures.alphaCommander().accountType())
        .isEqualTo(AccountType.COMMAND);
    assertThat(AccountPolicePhoneFixtures.alphaCommander().organizationType())
        .isEqualTo(OrganizationType.MISSING_TEAM);
    assertThat(AccountPolicePhoneFixtures.alphaCommander().roles())
        .containsExactlyInAnyOrder(Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER);

    assertThat(AccountPolicePhoneFixtures.precinctTeam().accountType()).isEqualTo(AccountType.TEAM);
    assertThat(AccountPolicePhoneFixtures.precinctTeam().roles()).containsExactly(Role.MEMBER);
    assertThat(AccountPolicePhoneFixtures.precinctPatrol().accountType())
        .isEqualTo(AccountType.PATROL_CAR);
    assertThat(AccountPolicePhoneFixtures.precinctPatrol().roles()).containsExactly(Role.MEMBER);
  }

  @Test
  @DisplayName("test seed loader exposes account policePhone and role channel matrix fixtures")
  void test_seed_loader_exposes_auth_fixture_baseline() {
    AccountPolicePhoneSeedLoader.AuthSeed seed = AccountPolicePhoneSeedLoader.load();

    assertThat(seed.accounts()).hasSize(8);
    assertThat(seed.policePhones()).hasSize(8);
    assertThat(seed.roleChannelRules()).containsAll(RoleChannelMatrixFixtures.rules());
    assertThat(seed.accountByCode("acct-support-car").orElseThrow().accountType())
        .isEqualTo(AccountType.PATROL_CAR);
    assertThat(seed.policePhoneByCode("dev-support-phone-01").orElseThrow().accountId())
        .isEqualTo(AccountIdentityCatalog.SUPPORT_TEAM_ID);
  }

  @Test
  @DisplayName("role channel matrix fixture freezes APP WEB permission rows without guard behavior")
  void role_channel_matrix_fixture_freezes_boundaries_matrix_rows() {
    assertThat(RoleChannelMatrixFixtures.rules()).hasSize(14);
    assertThat(RoleChannelMatrixFixtures.rule("사건 가져오기").allowedChannels()).containsExactly("WEB");
    assertThat(RoleChannelMatrixFixtures.rule("수색 세션").allowedChannels()).containsExactly("APP");
    assertThat(RoleChannelMatrixFixtures.rule("마커 수정·삭제").allowedChannels())
        .containsExactlyInAnyOrder("APP", "WEB");
    assertThat(RoleChannelMatrixFixtures.rule("상황판 조회").requiredRoleText()).isEqualTo("사건 배정 계정");
  }
}
