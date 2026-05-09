package com.surimap.account.harness;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.policephone.PolicePhoneFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** L2-T09A shared auth/policePhone fixture values for mock and real contract adapters. */
public final class AuthPolicePhoneHarnessFixtures {

  public static final String INCIDENT_ID = "inc-precinct-first-001";
  public static final String OP1_ID = "op-precinct-001-op1";
  public static final String PRECINCT_TEAM_ACCOUNT_ID = "acct-precinct-team";
  public static final String PRECINCT_TEAM_POLICE_PHONE_ALIAS = "dev-precinct-phone-01";
  public static final UUID PRECINCT_TEAM_POLICE_PHONE_ID =
      PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
  public static final UUID UNREGISTERED_POLICE_PHONE_ID =
      UUID.nameUUIDFromBytes("l2-t09a-unregistered-police-phone".getBytes(StandardCharsets.UTF_8));
  public static final UUID UNASSIGNED_POLICE_PHONE_ID =
      PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID;

  private AuthPolicePhoneHarnessFixtures() {}

  public static HarnessFixture precinctTeamApp() {
    return new HarnessFixture(
        precinctTeamContext(Channel.APP, PRECINCT_TEAM_POLICE_PHONE_ALIAS, PRECINCT_TEAM_POLICE_PHONE_ID),
        new PolicePhoneAssignment(
            PRECINCT_TEAM_POLICE_PHONE_ALIAS,
            PRECINCT_TEAM_POLICE_PHONE_ID,
            PRECINCT_TEAM_ACCOUNT_ID,
            INCIDENT_ID,
            OP1_ID,
            true,
            true));
  }

  public static HarnessFixture precinctTeamWeb() {
    return new HarnessFixture(
        precinctTeamContext(Channel.WEB, PRECINCT_TEAM_POLICE_PHONE_ALIAS, PRECINCT_TEAM_POLICE_PHONE_ID),
        new PolicePhoneAssignment(
            PRECINCT_TEAM_POLICE_PHONE_ALIAS,
            PRECINCT_TEAM_POLICE_PHONE_ID,
            PRECINCT_TEAM_ACCOUNT_ID,
            INCIDENT_ID,
            OP1_ID,
            true,
            true));
  }

  public static HarnessFixture alphaCommanderWeb() {
    return new HarnessFixture(
        new HarnessContext(
            "acct-cmd-alpha",
            Channel.WEB,
            AccountType.COMMAND,
            OrganizationType.MISSING_TEAM,
            "dev-alpha-cmd-phone-01",
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
            List.of(Role.MISSING_TEAM_COMMANDER.name(), Role.FIELD_COMMANDER.name())),
        new PolicePhoneAssignment(
            "dev-alpha-cmd-phone-01",
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
            "acct-cmd-alpha",
            INCIDENT_ID,
            OP1_ID,
            true,
            true));
  }

  public static HarnessFixture missingPolicePhoneApp() {
    return new HarnessFixture(
        precinctTeamContext(Channel.APP, null, null),
        new PolicePhoneAssignment(null, null, PRECINCT_TEAM_ACCOUNT_ID, INCIDENT_ID, OP1_ID, false, false));
  }

  public static HarnessFixture unregisteredPolicePhoneApp() {
    return new HarnessFixture(
        precinctTeamContext(Channel.APP, "dev-unregistered-phone-01", UNREGISTERED_POLICE_PHONE_ID),
        new PolicePhoneAssignment(
            "dev-unregistered-phone-01",
            UNREGISTERED_POLICE_PHONE_ID,
            PRECINCT_TEAM_ACCOUNT_ID,
            INCIDENT_ID,
            OP1_ID,
            false,
            false));
  }

  public static HarnessFixture unassignedPolicePhoneApp() {
    return new HarnessFixture(
        precinctTeamContext(Channel.APP, "dev-unassigned-phone-01", UNASSIGNED_POLICE_PHONE_ID),
        new PolicePhoneAssignment(
            "dev-unassigned-phone-01",
            UNASSIGNED_POLICE_PHONE_ID,
            PRECINCT_TEAM_ACCOUNT_ID,
            INCIDENT_ID,
            OP1_ID,
            true,
            false));
  }

  /**
   * Compound-violation fixture: WEB channel + policePhoneId null.
   * Both {@code channel_not_allowed} and {@code police_phone_required} are violated simultaneously.
   * Per app-police-phone failure_precedence, {@code channel_not_allowed} is returned first.
   */
  public static HarnessFixture webChannelMissingPolicePhone() {
    return new HarnessFixture(
        precinctTeamContext(Channel.WEB, null, null),
        new PolicePhoneAssignment(null, null, PRECINCT_TEAM_ACCOUNT_ID, INCIDENT_ID, OP1_ID, false, false));
  }

  private static HarnessContext precinctTeamContext(
      Channel channel, String policePhoneAlias, UUID policePhoneId) {
    return new HarnessContext(
        PRECINCT_TEAM_ACCOUNT_ID,
        channel,
        AccountType.TEAM,
        OrganizationType.POLICE_SUBSTATION,
        policePhoneAlias,
        policePhoneId,
        List.of(Role.MEMBER.name()));
  }

  public record HarnessFixture(HarnessContext context, PolicePhoneAssignment policePhone) {}

  public record HarnessContext(
      String accountId,
      Channel channel,
      AccountType accountType,
      OrganizationType organizationType,
      String policePhoneAlias,
      UUID policePhoneId,
      List<String> authorities) {

    public HarnessContext {
      authorities = List.copyOf(authorities);
    }
  }

  public record PolicePhoneAssignment(
      String alias,
      UUID policePhoneId,
      String accountId,
      String incidentId,
      String opId,
      boolean registered,
      boolean assigned) {}

  public record GuardOutcome(boolean permitted, String errorCode) {

    public static GuardOutcome ok() {
      return new GuardOutcome(true, null);
    }

    public static GuardOutcome denied(String errorCode) {
      return new GuardOutcome(false, errorCode);
    }
  }
}
