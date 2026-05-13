package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.support.auth.WithMockAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class WithMockAccountFixtureContractTest {

  @Test
  @WithMockAccount(
      accountId = "acct-precinct-team",
      policePhoneId = "dev-precinct-phone-01",
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      channel = Channel.APP,
      roles = Role.MEMBER)
  @DisplayName("WithMockAccount maps harness login codes to UUID account IDs")
  void withMockAccount_maps_harness_login_codes_to_uuid_account_ids() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    assertThat(authentication).isInstanceOf(SuriMapAuthentication.class);
    var suriMapAuthentication = (SuriMapAuthentication) authentication;
    assertThat(suriMapAuthentication.getAccountId())
        .isEqualTo(AccountIdentityCatalog.PRECINCT_TEAM_ID.toString());
    assertThat(suriMapAuthentication.getPolicePhoneId()).isEqualTo("dev-precinct-phone-01");
    assertThat(suriMapAuthentication.getAccountType()).isEqualTo(AccountType.TEAM);
    assertThat(suriMapAuthentication.getOrganizationType())
        .isEqualTo(OrganizationType.POLICE_SUBSTATION);
    assertThat(suriMapAuthentication.getChannel()).isEqualTo(Channel.APP);
    assertThat(suriMapAuthentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("MEMBER");
  }

  @Test
  @WithMockAccount(
      accountId = "acct-cmd-alpha",
      policePhoneId = "dev-alpha-cmd-phone-01",
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER})
  @DisplayName("missing team commander fixture carries both commander authorities")
  void missing_team_commander_fixture_carries_both_commander_authorities() {
    var authentication =
        (SuriMapAuthentication) SecurityContextHolder.getContext().getAuthentication();

    assertThat(authentication.getAccountId())
        .isEqualTo(AccountIdentityCatalog.ALPHA_COMMANDER_ID.toString());
    assertThat(authentication.getPolicePhoneId()).isEqualTo("dev-alpha-cmd-phone-01");
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactlyInAnyOrder("MISSING_TEAM_COMMANDER", "FIELD_COMMANDER");
  }
}
