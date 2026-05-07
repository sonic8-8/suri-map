package com.surimap.account.fixture;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import java.util.List;

/** S1-2 account/police_phone harness fixture IDs from docs/spec/harness-scenarios.md §6. */
public final class AccountPolicePhoneFixtures {

  public static final String INCIDENT_ID = "inc-precinct-first-001";
  public static final String OP1_ID = "op-precinct-001-op1";

  private static final AccountFixture PRECINCT_COMMANDER =
      new AccountFixture(
          "acct-precinct-cmd",
          "team-precinct-jongno",
          "dev-precinct-cmd-phone-01",
          AccountType.COMMAND,
          OrganizationType.POLICE_SUBSTATION,
          List.of(Role.FIELD_COMMANDER));

  private static final AccountFixture PRECINCT_PATROL =
      new AccountFixture(
          "acct-precinct-car",
          "team-precinct-jongno",
          "dev-precinct-car-01",
          AccountType.PATROL_CAR,
          OrganizationType.POLICE_SUBSTATION,
          List.of(Role.MEMBER));

  private static final AccountFixture PRECINCT_TEAM =
      new AccountFixture(
          "acct-precinct-team",
          "team-precinct-jongno",
          "dev-precinct-phone-01",
          AccountType.TEAM,
          OrganizationType.POLICE_SUBSTATION,
          List.of(Role.MEMBER));

  private static final AccountFixture ALPHA_COMMANDER =
      new AccountFixture(
          "acct-cmd-alpha",
          "team-missing-alpha",
          "dev-alpha-cmd-phone-01",
          AccountType.COMMAND,
          OrganizationType.MISSING_TEAM,
          List.of(Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER));

  private static final AccountFixture ALPHA_TEAM =
      new AccountFixture(
          "acct-team-alpha",
          "team-missing-alpha",
          "dev-alpha-phone-01",
          AccountType.TEAM,
          OrganizationType.MISSING_TEAM,
          List.of(Role.MEMBER));

  private static final AccountFixture SUPPORT_COMMANDER =
      new AccountFixture(
          "acct-support-cmd",
          "team-support-bravo",
          "dev-support-cmd-phone-01",
          AccountType.COMMAND,
          OrganizationType.SUPPORT_UNIT,
          List.of(Role.FIELD_COMMANDER));

  private static final AccountFixture SUPPORT_PATROL =
      new AccountFixture(
          "acct-support-car",
          "team-support-bravo",
          "dev-support-car-01",
          AccountType.PATROL_CAR,
          OrganizationType.SUPPORT_UNIT,
          List.of(Role.MEMBER));

  private static final AccountFixture SUPPORT_TEAM =
      new AccountFixture(
          "acct-support-team",
          "team-support-bravo",
          "dev-support-phone-01",
          AccountType.TEAM,
          OrganizationType.SUPPORT_UNIT,
          List.of(Role.MEMBER));

  private AccountPolicePhoneFixtures() {}

  public static List<AccountFixture> accounts() {
    return List.of(
        PRECINCT_COMMANDER,
        PRECINCT_PATROL,
        PRECINCT_TEAM,
        ALPHA_COMMANDER,
        ALPHA_TEAM,
        SUPPORT_COMMANDER,
        SUPPORT_PATROL,
        SUPPORT_TEAM);
  }

  public static List<PolicePhoneFixture> policePhones() {
    return accounts().stream()
        .map(account -> new PolicePhoneFixture(account.policePhoneId(), account.id(), true))
        .toList();
  }

  public static List<String> accountIds() {
    return accounts().stream().map(AccountFixture::id).toList();
  }

  public static List<String> policePhoneIds() {
    return policePhones().stream().map(PolicePhoneFixture::id).toList();
  }

  public static AccountFixture precinctCommander() {
    return PRECINCT_COMMANDER;
  }

  public static AccountFixture precinctTeam() {
    return PRECINCT_TEAM;
  }

  public static AccountFixture precinctPatrol() {
    return PRECINCT_PATROL;
  }

  public static AccountFixture alphaCommander() {
    return ALPHA_COMMANDER;
  }

  public record AccountFixture(
      String id,
      String teamId,
      String policePhoneId,
      AccountType accountType,
      OrganizationType organizationType,
      List<Role> roles) {}

  public record PolicePhoneFixture(String id, String accountId, boolean registered) {}
}
