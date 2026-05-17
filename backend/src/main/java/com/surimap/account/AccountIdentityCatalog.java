package com.surimap.account;

import java.util.Map;
import java.util.UUID;

/** Temporary S1-2 fixture identity catalog until account persistence is introduced. */
public final class AccountIdentityCatalog {

  public static final String PRECINCT_COMMANDER_CODE = "acct-precinct-cmd";
  public static final String PRECINCT_PATROL_CODE = "acct-precinct-car";
  public static final String PRECINCT_TEAM_CODE = "acct-precinct-team";
  public static final String ALPHA_COMMANDER_CODE = "acct-cmd-alpha";
  public static final String ALPHA_TEAM_CODE = "acct-team-alpha";
  public static final String SUPPORT_COMMANDER_CODE = "acct-support-cmd";
  public static final String SUPPORT_PATROL_CODE = "acct-support-car";
  public static final String SUPPORT_TEAM_CODE = "acct-support-team";
  public static final String UNASSIGNED_PHONE_CODE = "acct-unassigned-phone";

  public static final UUID PRECINCT_COMMANDER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110001");
  public static final UUID PRECINCT_PATROL_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110002");
  public static final UUID PRECINCT_TEAM_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110003");
  public static final UUID ALPHA_COMMANDER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110004");
  public static final UUID ALPHA_TEAM_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110005");
  public static final UUID SUPPORT_COMMANDER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110006");
  public static final UUID SUPPORT_PATROL_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110007");
  public static final UUID SUPPORT_TEAM_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110008");
  public static final UUID UNASSIGNED_PHONE_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110009");

  private static final Map<String, UUID> IDS_BY_CODE =
      Map.of(
          PRECINCT_COMMANDER_CODE, PRECINCT_COMMANDER_ID,
          PRECINCT_PATROL_CODE, PRECINCT_PATROL_ID,
          PRECINCT_TEAM_CODE, PRECINCT_TEAM_ID,
          ALPHA_COMMANDER_CODE, ALPHA_COMMANDER_ID,
          ALPHA_TEAM_CODE, ALPHA_TEAM_ID,
          SUPPORT_COMMANDER_CODE, SUPPORT_COMMANDER_ID,
          SUPPORT_PATROL_CODE, SUPPORT_PATROL_ID,
          SUPPORT_TEAM_CODE, SUPPORT_TEAM_ID,
          UNASSIGNED_PHONE_CODE, UNASSIGNED_PHONE_ID);

  private AccountIdentityCatalog() {}

  public static UUID accountIdFromCodeOrUuid(String accountCodeOrId) {
    if (accountCodeOrId == null || accountCodeOrId.isBlank()) {
      throw new IllegalArgumentException("accountCodeOrId must not be blank");
    }
    UUID mapped = IDS_BY_CODE.get(accountCodeOrId);
    if (mapped != null) {
      return mapped;
    }
    return UUID.fromString(accountCodeOrId);
  }
}
