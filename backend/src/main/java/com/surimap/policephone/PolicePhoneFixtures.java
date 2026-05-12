package com.surimap.policephone;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import java.util.UUID;

/** Stable S1-2 fixture IDs for police-phone assignment and heartbeat flows. */
public final class PolicePhoneFixtures {

  public static final UUID INCIDENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  public static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
  public static final UUID ASSIGNED_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");
  public static final UUID ASSIGNED_PATH_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000000001");
  public static final UUID REGISTERED_UNASSIGNED_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000301");
  public static final String ASSIGNED_APP_INSTANCE_ID = "app-instance-assigned-101";
  public static final String ASSIGNED_APP_TOKEN = "fcm-token-assigned-101";
  public static final String ASSIGNED_APP_TOKEN_ROTATED = "fcm-token-assigned-101-rotated";
  public static final String PATH_APP_INSTANCE_ID = "app-instance-path-500";
  public static final String PATH_APP_TOKEN = "fcm-token-path-500";
  public static final String ASSIGNED_ACCOUNT_CODE = AccountIdentityCatalog.PRECINCT_TEAM_CODE;
  public static final String ASSIGNED_ACCOUNT_ID = AccountIdentityCatalog.PRECINCT_TEAM_ID.toString();
  public static final AccountType ASSIGNED_ACCOUNT_TYPE = AccountType.TEAM;
  public static final OrganizationType ASSIGNED_ORGANIZATION_TYPE = OrganizationType.POLICE_SUBSTATION;

  private PolicePhoneFixtures() {}
}
