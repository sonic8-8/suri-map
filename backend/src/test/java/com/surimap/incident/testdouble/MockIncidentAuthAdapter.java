package com.surimap.incident.testdouble;

import com.surimap.incident.fixture.IncidentSeedFixtureIds;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** S1-2 권한·사건 배정 mock. SecurityException 메시지는 spec error code를 그대로 사용한다. */
public final class MockIncidentAuthAdapter {

  private final IncidentSeedFixtureIds seed;
  private final LinkedHashSet<String> assignedAccountIds = new LinkedHashSet<>();

  public MockIncidentAuthAdapter(IncidentSeedFixtureIds seed) {
    this.seed = Objects.requireNonNull(seed, "seed는 null일 수 없습니다");
    assignedAccountIds.add("acct-precinct-cmd");
    assignedAccountIds.add("acct-precinct-car");
    assignedAccountIds.add("acct-precinct-team");
  }

  public MockSecurityContext webPrecinctCommander() {
    return context("acct-precinct-cmd", "COMMAND", "POLICE_SUBSTATION", "WEB", "FIELD_COMMANDER");
  }

  public MockSecurityContext webMissingTeamCommander() {
    return context("acct-cmd-alpha", "COMMAND", "MISSING_TEAM", "WEB", "MISSING_TEAM_COMMANDER");
  }

  public MockSecurityContext webSupportCommander() {
    return context("acct-support-cmd", "COMMAND", "SUPPORT_UNIT", "WEB", "FIELD_COMMANDER");
  }

  /**
   * 사건 가져오기 권한을 검증한다. WEB 채널 + (실종팀 지휘관 또는 지구대/파출소 FIELD_COMMANDER) 조건을 만족하지 않으면 SecurityException을
   * throw한다.
   */
  public void requireImportAllowed(MockSecurityContext context) {
    if (!"WEB".equals(context.channel())) {
      throw new SecurityException("channel_not_allowed");
    }
    boolean missingTeamCommander =
        "MISSING_TEAM".equals(context.organizationType())
            && context.authorities().contains("MISSING_TEAM_COMMANDER");
    boolean precinctFieldCommander =
        "POLICE_SUBSTATION".equals(context.organizationType())
            && "COMMAND".equals(context.accountType())
            && context.authorities().contains("FIELD_COMMANDER");
    if (!missingTeamCommander && !precinctFieldCommander) {
      throw new SecurityException("role_denied");
    }
  }

  public void requireIncidentAssigned(MockSecurityContext context) {
    if (!assignedAccountIds.contains(context.accountId())) {
      throw new SecurityException("incident_access_denied");
    }
  }

  public void assignHandoverAccounts() {
    assignedAccountIds.add("acct-cmd-alpha");
    assignedAccountIds.add("acct-team-alpha");
  }

  public void assignSupportAccounts() {
    assignedAccountIds.add("acct-support-cmd");
    assignedAccountIds.add("acct-support-car");
    assignedAccountIds.add("acct-support-team");
  }

  public List<String> assignedAccountIds() {
    return List.copyOf(assignedAccountIds);
  }

  private MockSecurityContext context(
      String accountId,
      String accountType,
      String organizationType,
      String channel,
      String authority) {
    if (!seed.accountIds().contains(accountId)) {
      throw new IllegalArgumentException("fixture에 없는 accountId입니다: " + accountId);
    }
    return new MockSecurityContext(
        accountId, accountType, organizationType, channel, Set.of(authority));
  }

  public record MockSecurityContext(
      String accountId,
      String accountType,
      String organizationType,
      String channel,
      Set<String> authorities) {

    public MockSecurityContext {
      authorities = Set.copyOf(authorities);
    }
  }
}
