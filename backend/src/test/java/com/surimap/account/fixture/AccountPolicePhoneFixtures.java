package com.surimap.account.fixture;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import java.util.List;
import java.util.UUID;

/**
 * S1-2 account/police_phone harness fixture IDs and codes from docs/spec/harness-scenarios.md §6.
 */
public final class AccountPolicePhoneFixtures {

  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final String OP1_ALIAS = "op-precinct-001-op1";

  private static final AccountFixture PRECINCT_COMMANDER =
      new AccountFixture(
          AccountIdentityCatalog.PRECINCT_COMMANDER_ID,
          AccountIdentityCatalog.PRECINCT_COMMANDER_CODE,
          "team-precinct-jongno",
          UUID.fromString("00000000-0000-0000-0000-000000000201"),
          "dev-precinct-cmd-phone-01",
          AccountType.COMMAND,
          OrganizationType.POLICE_SUBSTATION,
          List.of(Role.FIELD_COMMANDER));

  private static final AccountFixture PRECINCT_PATROL =
      new AccountFixture(
          AccountIdentityCatalog.PRECINCT_PATROL_ID,
          AccountIdentityCatalog.PRECINCT_PATROL_CODE,
          "team-precinct-jongno",
          UUID.fromString("50000000-0000-0000-0000-000000000001"),
          "dev-precinct-car-01",
          AccountType.PATROL_CAR,
          OrganizationType.POLICE_SUBSTATION,
          List.of(Role.MEMBER));

  private static final AccountFixture PRECINCT_TEAM =
      new AccountFixture(
          AccountIdentityCatalog.PRECINCT_TEAM_ID,
          AccountIdentityCatalog.PRECINCT_TEAM_CODE,
          "team-precinct-jongno",
          UUID.fromString("00000000-0000-0000-0000-000000000101"),
          "dev-precinct-phone-01",
          AccountType.TEAM,
          OrganizationType.POLICE_SUBSTATION,
          List.of(Role.MEMBER));

  private static final AccountFixture ALPHA_COMMANDER =
      new AccountFixture(
          AccountIdentityCatalog.ALPHA_COMMANDER_ID,
          AccountIdentityCatalog.ALPHA_COMMANDER_CODE,
          "team-missing-alpha",
          UUID.fromString("00000000-0000-0000-0000-000000000204"),
          "dev-alpha-cmd-phone-01",
          AccountType.COMMAND,
          OrganizationType.MISSING_TEAM,
          List.of(Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER));

  private static final AccountFixture ALPHA_TEAM =
      new AccountFixture(
          AccountIdentityCatalog.ALPHA_TEAM_ID,
          AccountIdentityCatalog.ALPHA_TEAM_CODE,
          "team-missing-alpha",
          UUID.fromString("00000000-0000-0000-0000-000000000205"),
          "dev-alpha-phone-01",
          AccountType.TEAM,
          OrganizationType.MISSING_TEAM,
          List.of(Role.MEMBER));

  private static final AccountFixture SUPPORT_COMMANDER =
      new AccountFixture(
          AccountIdentityCatalog.SUPPORT_COMMANDER_ID,
          AccountIdentityCatalog.SUPPORT_COMMANDER_CODE,
          "team-support-bravo",
          UUID.fromString("00000000-0000-0000-0000-000000000206"),
          "dev-support-cmd-phone-01",
          AccountType.COMMAND,
          OrganizationType.SUPPORT_UNIT,
          List.of(Role.FIELD_COMMANDER));

  private static final AccountFixture SUPPORT_PATROL =
      new AccountFixture(
          AccountIdentityCatalog.SUPPORT_PATROL_ID,
          AccountIdentityCatalog.SUPPORT_PATROL_CODE,
          "team-support-bravo",
          UUID.fromString("00000000-0000-0000-0000-000000000207"),
          "dev-support-car-01",
          AccountType.PATROL_CAR,
          OrganizationType.SUPPORT_UNIT,
          List.of(Role.MEMBER));

  private static final AccountFixture SUPPORT_TEAM =
      new AccountFixture(
          AccountIdentityCatalog.SUPPORT_TEAM_ID,
          AccountIdentityCatalog.SUPPORT_TEAM_CODE,
          "team-support-bravo",
          UUID.fromString("00000000-0000-0000-0000-000000000208"),
          "dev-support-phone-01",
          AccountType.TEAM,
          OrganizationType.SUPPORT_UNIT,
          List.of(Role.MEMBER));

  private static final AccountFixture UNASSIGNED_PHONE =
      new AccountFixture(
          AccountIdentityCatalog.UNASSIGNED_PHONE_ID,
          AccountIdentityCatalog.UNASSIGNED_PHONE_CODE,
          "team-precinct-jongno",
          UUID.fromString("00000000-0000-0000-0000-000000000301"),
          "dev-unassigned-phone-01",
          AccountType.TEAM,
          OrganizationType.POLICE_SUBSTATION,
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

  public static List<AccountFixture> accountsIncludingUnassigned() {
    return List.of(
        PRECINCT_COMMANDER,
        PRECINCT_PATROL,
        PRECINCT_TEAM,
        ALPHA_COMMANDER,
        ALPHA_TEAM,
        SUPPORT_COMMANDER,
        SUPPORT_PATROL,
        SUPPORT_TEAM,
        UNASSIGNED_PHONE);
  }

  public static List<PolicePhoneFixture> policePhones() {
    return accounts().stream()
        .map(
            account ->
                new PolicePhoneFixture(
                    account.policePhoneId(), account.policePhoneCode(), account.id(), true))
        .toList();
  }

  public static List<UUID> accountIds() {
    return accounts().stream().map(AccountFixture::id).toList();
  }

  public static List<String> accountCodes() {
    return accounts().stream().map(AccountFixture::accountCode).toList();
  }

  public static List<UUID> policePhoneIds() {
    return policePhones().stream().map(PolicePhoneFixture::id).toList();
  }

  public static List<String> policePhoneCodes() {
    return policePhones().stream().map(PolicePhoneFixture::phoneCode).toList();
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

  public static AccountFixture unassignedPhone() {
    return UNASSIGNED_PHONE;
  }

  public record AccountFixture(
      UUID id,
      String accountCode,
      String teamCode,
      UUID policePhoneId,
      String policePhoneCode,
      AccountType accountType,
      OrganizationType organizationType,
      List<Role> roles) {}

  public record PolicePhoneFixture(UUID id, String phoneCode, UUID accountId, boolean registered) {}
}
